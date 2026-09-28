<template>
  <div style="position: relative;">
    <div
      v-if="type === '2'"
      class="verify-img-out"
      :style="{height: (parseInt(setSize.imgHeight) + vSpace) + 'px'}"
    >
      <div
        class="verify-img-panel"
        :style="{width: setSize.imgWidth,
                 height: setSize.imgHeight,}"
      >
        <canvas
          ref="canvasRef"
          :width="canvasWidth"
          :height="canvasHeight"
          style="width:100%;height:100%;display:block;"
        ></canvas>
        <div v-show="showRefresh" class="verify-refresh" @click="refresh"><i class="iconfont icon-refresh" />
        </div>
        <transition name="tips">
          <span v-if="tipWords" class="verify-tips" :class="passFlag ?'suc-bg':'err-bg'">{{ tipWords }}</span>
        </transition>
      </div>
    </div>
    <!-- 公共部分 -->
    <div
      class="verify-bar-area"
      :style="{width: setSize.imgWidth,
               height: barSize.height,
               'line-height':barSize.height}"
    >
      <span class="verify-msg" v-text="text" />
      <div
        class="verify-left-bar"
        :style="{width: (leftBarWidth!==undefined)?leftBarWidth: barSize.height, height: barSize.height, 'border-color': leftBarBorderColor, transaction: transitionWidth}"
      >
        <span class="verify-msg" v-text="finishText" />
        <div
          class="verify-move-block"
          :style="{width: barSize.height, height: barSize.height, 'background-color': moveBlockBackgroundColor, left: moveBlockLeft, transition: transitionLeft}"
          @touchstart="start"
          @mousedown="start"
        >
          <i
            :class="['verify-icon iconfont', iconClass]"
            :style="{color: iconColor}"
          />
        </div>
      </div>
    </div>
  </div>
</template>
<script type="text/babel">
/**
      * VerifyCurve V3
      * @description 滑块曲线验证码（端点固定，拖动改变弧度）
      * 后端返回底图(含目标曲线) + 曲线参数JSON,
      * 前端Canvas绘制底图+活动曲线, 拖动滑块改变活动曲线控制点x,
      * check时传活动曲线控制点x坐标.
      * */
import { aesEncrypt } from './../utils/ase'
import { resetSize } from './../utils/util'
import { reqGet, reqCheck } from './../api/index'

//  "captchaType":"curveSlider",
export default {
  name: 'VerifyCurve',
  props: {
    captchaType: {
      type: String,
    },
    type: {
      type: String,
      default: '1'
    },
    // 弹出式pop，固定fixed
    mode: {
      type: String,
      default: 'fixed'
    },
    vSpace: {
      type: Number,
      default: 5
    },
    explain: {
      type: String,
      default: '向右拖动滑块使曲线重合'
    },
    imgSize: {
      type: Object,
      default() {
        return {
          width: '310px',
          height: '155px'
        }
      }
    },
    blockSize: {
      type: Object,
      default() {
        return {
          width: '50px',
          height: '50px'
        }
      }
    },
    barSize: {
      type: Object,
      default() {
        return {
          width: '310px',
          height: '40px'
        }
      }
    },
    defaultImg: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      secretKey: '', // 后端返回的加密秘钥 字段
      passFlag: '', // 是否通过的标识
      backImgBase: '', // 验证码背景图片base64
      backToken: '', // 后端返回的唯一token值
      startMoveTime: '', // 移动开始的时间
      endMovetime: '', // 移动结束的时间
      tipsBackColor: '', // 提示词的背景颜色
      tipWords: '',
      text: '',
      finishText: '',
      setSize: {
        imgHeight: 0,
        imgWidth: 0,
        barHeight: 0,
        barWidth: 0
      },
      top: 0,
      left: 0,
      moveBlockLeft: undefined,
      leftBarWidth: undefined,
      // 移动中样式
      moveBlockBackgroundColor: undefined,
      leftBarBorderColor: '#ddd',
      iconColor: undefined,
      iconClass: 'icon-right',
      status: false, // 鼠标状态
      isEnd: false,		// 是够验证完成
      showRefresh: true,
      transitionLeft: '',
      transitionWidth: '',
      startLeft: 0,
      // Canvas
      canvasWidth: 310,
      canvasHeight: 155,
      // 曲线参数（从后端JSON解析）
      curveParams: null,
      // 活动曲线控制点x
      activeCtrlX: 0,
      // 底图Image对象
      bgImage: null
    }
  },
  computed: {
    barArea() {
      return this.$el.querySelector('.verify-bar-area')
    },
    resetSize() {
      return resetSize
    }
  },
  watch: {
    // type变化则全面刷新
    type: {
      immediate: true,
      handler() {
        this.init()
      }
    }
  },
  mounted() {
    // 禁止拖拽
    this.$el.onselectstart = function() {
      return false
    }
  },
  methods: {
    // === Canvas 绘制 ===
    loadBgAndDraw() {
      if (!this.backImgBase || !this.$refs.canvasRef) return
      var _this = this
      _this.bgImage = new Image()
      _this.bgImage.onload = function() {
        _this.canvasWidth = _this.bgImage.width
        _this.canvasHeight = _this.bgImage.height
        _this.$nextTick(function() {
          _this.drawCanvas()
        })
      }
      _this.bgImage.src = 'data:image/png;base64,' + _this.backImgBase
    },

    drawCanvas() {
      var canvas = this.$refs.canvasRef
      if (!canvas || !this.bgImage) return
      var ctx = canvas.getContext('2d')
      var w = this.canvasWidth
      var h = this.canvasHeight
      ctx.clearRect(0, 0, w, h)
      // 绘制底图
      ctx.drawImage(this.bgImage, 0, 0, w, h)
      // 绘制活动曲线（蓝色半透明）
      if (this.curveParams) {
        this.drawActiveCurve(ctx, this.activeCtrlX)
      }
    },

    drawActiveCurve(ctx, ctrlX) {
      if (!this.curveParams) return
      var p = this.curveParams
      ctx.beginPath()
      ctx.moveTo(p.startX, p.topY)
      ctx.quadraticCurveTo(ctrlX, p.bottomY, p.endX, p.topY)
      ctx.lineWidth = 3
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.strokeStyle = 'rgba(51, 122, 183, 0.7)'
      ctx.stroke()
      // 蓝色端点圆
      ctx.fillStyle = 'rgba(51, 122, 183, 0.8)'
      ctx.beginPath()
      ctx.arc(p.startX, p.topY, 5, 0, Math.PI * 2)
      ctx.fill()
      ctx.beginPath()
      ctx.arc(p.endX, p.topY, 5, 0, Math.PI * 2)
      ctx.fill()
    },

    // 计算活动曲线控制点x（基于滑块拖动距离）
    calcActiveCtrlX(moveLeftDistance) {
      if (!this.curveParams) return 0
      var p = this.curveParams
      var maxSlide = p.imgWidth - parseInt(this.blockSize.width)
      if (maxSlide <= 0) maxSlide = p.imgWidth
      var ratio = moveLeftDistance / maxSlide
      if (ratio < 0) ratio = 0
      if (ratio > 1) ratio = 1
      return Math.round(p.ctrlXMin + ratio * (p.ctrlXMax - p.ctrlXMin))
    },

    init() {
      this.text = this.explain
      this.getPictrue()
      this.$nextTick(() => {
        const setSize = this.resetSize(this)	// 重新设置宽度高度
        for (const key in setSize) {
          this.$set(this.setSize, key, setSize[key])
        }
        this.$parent.$emit('ready', this)
      })

      var _this = this

      window.removeEventListener('touchmove', function(e) {
        _this.move(e)
      })
      window.removeEventListener('mousemove', function(e) {
        _this.move(e)
      })

      // 鼠标松开
      window.removeEventListener('touchend', function() {
        _this.end()
      })
      window.removeEventListener('mouseup', function() {
        _this.end()
      })

      window.addEventListener('touchmove', function(e) {
        _this.move(e)
      })
      window.addEventListener('mousemove', function(e) {
        _this.move(e)
      })

      // 鼠标松开
      window.addEventListener('touchend', function() {
        _this.end()
      })
      window.addEventListener('mouseup', function() {
        _this.end()
      })
    },

    // 鼠标按下
    start: function(e) {
      e = e || window.event
      if (!e.touches) { // 兼容PC端
        var x = e.clientX
      } else { // 兼容移动端
        var x = e.touches[0].pageX
      }
      this.startLeft = Math.floor(x - this.barArea.getBoundingClientRect().left)
      this.startMoveTime = +new Date() // 开始滑动的时间
      if (this.isEnd == false) {
        this.text = ''
        this.moveBlockBackgroundColor = '#337ab7'
        this.leftBarBorderColor = '#337AB7'
        this.iconColor = '#fff'
        e.stopPropagation()
        this.status = true
      }
    },
    // 鼠标移动
    move: function(e) {
      e = e || window.event
      if (this.status && this.isEnd == false) {
        if (!e.touches) { // 兼容PC端
          var x = e.clientX
        } else { // 兼容移动端
          var x = e.touches[0].pageX
        }
        var bar_area_left = this.barArea.getBoundingClientRect().left
        var move_block_left = x - bar_area_left // 小方块相对于父元素的left值
        if (move_block_left >= this.barArea.offsetWidth - parseInt(parseInt(this.blockSize.width) / 2) - 2) {
          move_block_left = this.barArea.offsetWidth - parseInt(parseInt(this.blockSize.width) / 2) - 2
        }
        if (move_block_left <= 0) {
          move_block_left = parseInt(parseInt(this.blockSize.width) / 2)
        }
        // 拖动后小方块的left值
        var pixelLeft = move_block_left - this.startLeft
        this.moveBlockLeft = pixelLeft + 'px'
        this.leftBarWidth = pixelLeft + 'px'

        // 按图片原始尺寸比例换算
        var scaledLeft = pixelLeft * 310 / parseInt(this.setSize.imgWidth)
        // 更新活动曲线
        this.activeCtrlX = this.calcActiveCtrlX(scaledLeft)
        this.drawCanvas()
      }
    },

    // 鼠标松开
    end: function() {
      this.endMovetime = +new Date()
      var _this = this
      // 判断是否重合
      if (this.status && this.isEnd == false) {
        var moveLeftDistance = parseInt((this.moveBlockLeft || '').replace('px', ''))
        moveLeftDistance = moveLeftDistance * 310 / parseInt(this.setSize.imgWidth)
        // 曲线滑块: 传活动曲线控制点x坐标, y固定传0
        const data = {
          captchaType: this.captchaType,
          'pointJson': this.secretKey ? aesEncrypt(JSON.stringify({ x: this.activeCtrlX, y: 0 }), this.secretKey) : JSON.stringify({ x: this.activeCtrlX, y: 0 }),
          'token': this.backToken
        }
        reqCheck(data).then(res => {
          if (res.repCode == '0000') {
            this.moveBlockBackgroundColor = '#5cb85c'
            this.leftBarBorderColor = '#5cb85c'
            this.iconColor = '#fff'
            this.iconClass = 'icon-check'
            this.showRefresh = false
            this.isEnd = true
            if (this.mode == 'pop') {
              setTimeout(() => {
                this.$parent.clickShow = false
                this.refresh()
              }, 1500)
            }
            this.passFlag = true
            this.tipWords = `${((this.endMovetime - this.startMoveTime) / 1000).toFixed(2)}s验证成功`
            var captchaVerification = this.secretKey ? aesEncrypt(this.backToken + '---' + JSON.stringify({ x: this.activeCtrlX, y: 0 }), this.secretKey) : this.backToken + '---' + JSON.stringify({ x: this.activeCtrlX, y: 0 })
            setTimeout(() => {
              this.tipWords = ''
              this.$parent.closeBox()
              this.$parent.$emit('success', { captchaVerification })
            }, 1000)
          } else {
            this.moveBlockBackgroundColor = '#d9534f'
            this.leftBarBorderColor = '#d9534f'
            this.iconColor = '#fff'
            this.iconClass = 'icon-close'
            this.passFlag = false
            setTimeout(function() {
              _this.refresh()
            }, 1000)
            this.$parent.$emit('error', this)
            this.tipWords = '验证失败'
            setTimeout(() => {
              this.tipWords = ''
            }, 1000)
          }
        })
        this.status = false
      }
    },

    refresh: function() {
      this.showRefresh = true
      this.finishText = ''

      this.transitionLeft = 'left .3s'
      this.moveBlockLeft = 0

      this.leftBarWidth = undefined
      this.transitionWidth = 'width .3s'

      this.leftBarBorderColor = '#ddd'
      this.moveBlockBackgroundColor = '#fff'
      this.iconColor = '#000'
      this.iconClass = 'icon-right'
      this.isEnd = false

      this.getPictrue()
      setTimeout(() => {
        this.transitionWidth = ''
        this.transitionLeft = ''
        this.text = this.explain
      }, 300)
    },

    // 请求背景图片和验证图片
    getPictrue() {
      const data = {
        captchaType: this.captchaType,
        clientUid: localStorage.getItem('slider'),
        ts: Date.now(), // 现在的时间戳
      }
      var _this = this
      reqGet(data).then(res => {
        if (res.repCode == '0000') {
          this.backImgBase = res.repData.originalImageBase64
          this.backToken = res.repData.token
          this.secretKey = res.repData.secretKey
          // 解析曲线参数JSON
          try {
            this.curveParams = JSON.parse(res.repData.jigsawImageBase64)
          } catch (e) {
            console.error('曲线参数解析失败', e)
            this.curveParams = null
          }
          // 初始活动曲线控制点x = ctrlXMin
          this.activeCtrlX = this.curveParams ? this.curveParams.ctrlXMin : 0
          // 加载底图并绘制Canvas
          this.loadBgAndDraw()
        } else {
          this.tipWords = res.repMsg
        }

        // 判断接口请求次数是否失效
        if (res.repCode == '6201') {
          this.backImgBase = null
        }
      })
    },
  },
}
</script>
