<template>
    <div style="position: relative;">
        <div v-if="type === '2'" class="verify-img-out"
             :style="{height: (parseInt(setSize.imgHeight) + vSpace) + 'px'}"
            >
            <div class="verify-img-panel" :style="{width: setSize.imgWidth,
                                                   height: setSize.imgHeight,}">
                <canvas ref="canvasRef"
                    :width="canvasWidth"
                    :height="canvasHeight"
                    style="width:100%;height:100%;display:block;">
                </canvas>
                <div class="verify-refresh" @click="refresh" v-show="showRefresh"><i class="iconfont icon-refresh"></i>
                </div>
                <transition name="tips">
                    <span class="verify-tips" v-if="tipWords" :class="passFlag ?'suc-bg':'err-bg'">{{tipWords}}</span>
                </transition>
            </div>
        </div>
        <!-- 公共部分 -->
        <div class="verify-bar-area" :style="{width: setSize.imgWidth,
                                              height: barSize.height,
                                              'line-height':barSize.height}">
            <span class="verify-msg" v-text="text"></span>
            <div class="verify-left-bar"
                 :style="{width: (leftBarWidth!==undefined)?leftBarWidth: barSize.height, height: barSize.height, 'border-color': leftBarBorderColor, transaction: transitionWidth}">
                <span class="verify-msg" v-text="finishText"></span>
                <div class="verify-move-block"
                     @touchstart="start"
                     @mousedown="start"
                     :style="{width: barSize.height, height: barSize.height, 'background-color': moveBlockBackgroundColor, left: moveBlockLeft, transition: transitionLeft}">
                    <i :class="['verify-icon iconfont', iconClass]"
                       :style="{color: iconColor}"></i>
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
    import {aesEncrypt} from "./../utils/ase"
    import {resetSize} from './../utils/util'
    import {reqGet,reqCheck}  from "./../api/index"
    import { computed, onMounted, reactive, ref,watch,nextTick,toRefs, watchEffect,getCurrentInstance} from 'vue';
    //  "captchaType":"curveSlider",
    export default {
        name: 'VerifyCurve',
        props: {
            captchaType:{
                type:String,
            },
            type: {
                type: String,
                default: '1'
            },
            //弹出式pop，固定fixed
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
            }
        },
        setup(props,context){
            const {mode,captchaType,vSpace,imgSize,barSize,type,blockSize,explain} = toRefs(props)
            const { proxy } = getCurrentInstance();
            let secretKey = ref(''),           //后端返回的ase加密秘钥
                passFlag = ref(''),         //是否通过的标识
                backImgBase = ref(''),      //验证码背景图片base64
                backToken = ref(''),        //后端返回的唯一token值
                startMoveTime = ref(''),    //移动开始的时间
                endMovetime = ref(''),      //移动结束的时间
                tipsBackColor = ref(''),    //提示词的背景颜色
                tipWords = ref(''),
                text = ref(''),
                finishText = ref(''),
                setSize = reactive({
                    imgHeight: 0,
                    imgWidth: 0,
                    barHeight: 0,
                    barWidth: 0
                }),
                top = ref(0),
                left = ref(0),
                moveBlockLeft = ref(undefined),
                leftBarWidth = ref(undefined),
                // 移动中样式
                moveBlockBackgroundColor = ref(undefined),
                leftBarBorderColor = ref('#ddd'),
                iconColor = ref(undefined),
                iconClass = ref('icon-right'),
                status = ref(false),	    //鼠标状态
                isEnd = ref(false) ,		//是够验证完成
                showRefresh = ref(true),
                transitionLeft = ref(''),
                transitionWidth = ref(''),
                startLeft = ref(0)

            // Canvas相关
            const canvasRef = ref(null)
            const canvasWidth = ref(310)
            const canvasHeight = ref(155)
            // 曲线参数（从后端JSON解析）
            let curveParams = null
            // 活动曲线控制点x（随滑块位置变化）
            let activeCtrlX = 0
            // 底图Image对象
            let bgImage = null

                const barArea = computed(()=>{
                    return proxy.$el.querySelector('.verify-bar-area')
                })
                function init() {
                    text.value = explain.value
                    getPictrue();
                    nextTick(() => {
                        let {imgHeight,imgWidth,barHeight,barWidth} = resetSize(proxy)
                        setSize.imgHeight = imgHeight
                        setSize.imgWidth = imgWidth
                        setSize.barHeight = barHeight
                        setSize.barWidth = barWidth
                        proxy.$parent.$emit('ready', proxy)
                    })

                    window.removeEventListener("touchmove", function (e) {
                        move(e);
                    });
                    window.removeEventListener("mousemove", function (e) {
                        move(e);
                    });

                    //鼠标松开
                    window.removeEventListener("touchend", function () {
                        end();
                    });
                    window.removeEventListener("mouseup", function () {
                        end();
                    });

                    window.addEventListener("touchmove", function (e) {
                        move(e);
                    });
                    window.addEventListener("mousemove", function (e) {
                        move(e);
                    });

                    //鼠标松开
                    window.addEventListener("touchend", function () {
                        end();
                    });
                    window.addEventListener("mouseup", function () {
                        end();
                    });
                }
                watch(type,()=>{
                    init()
                })
                onMounted(()=>{
                    // 禁止拖拽
                    init()
                    proxy.$el.onselectstart = function () {
                        return false
                    }
                })

                // === Canvas 绘制函数 ===
                function loadBgAndDraw() {
                    if (!backImgBase.value || !canvasRef.value) return
                    bgImage = new Image()
                    bgImage.onload = function() {
                        canvasWidth.value = bgImage.width
                        canvasHeight.value = bgImage.height
                        nextTick(() => {
                            drawCanvas()
                        })
                    }
                    bgImage.src = 'data:image/png;base64,' + backImgBase.value
                }

                function drawCanvas() {
                    if (!canvasRef.value || !bgImage) return
                    var ctx = canvasRef.value.getContext('2d')
                    var w = canvasWidth.value
                    var h = canvasHeight.value
                    ctx.clearRect(0, 0, w, h)
                    // 绘制底图
                    ctx.drawImage(bgImage, 0, 0, w, h)
                    // 绘制活动曲线（蓝色半透明）
                    if (curveParams) {
                        drawActiveCurve(ctx, activeCtrlX)
                    }
                }

                function drawActiveCurve(ctx, ctrlX) {
                    if (!curveParams) return
                    var p = curveParams
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
                }

                // 计算活动曲线控制点x（基于滑块拖动距离）
                // 滑块在bar中最左侧时 → ctrlXMin, 最右侧时 → ctrlXMax
                function calcActiveCtrlX(moveLeftDistance) {
                    if (!curveParams) return 0
                    var p = curveParams
                    // 滑块可移动范围: 0 ~ (imgWidth - blockSize)
                    var maxSlide = p.imgWidth - parseInt(blockSize.value.width)
                    if (maxSlide <= 0) maxSlide = p.imgWidth
                    var ratio = moveLeftDistance / maxSlide
                    if (ratio < 0) ratio = 0
                    if (ratio > 1) ratio = 1
                    return Math.round(p.ctrlXMin + ratio * (p.ctrlXMax - p.ctrlXMin))
                }

                // === 鼠标事件 ===
                //鼠标按下
                function start(e) {
                    e = e || window.event
                    if (!e.touches) {  //兼容PC端 
                        var x = e.clientX;
                    } else {           //兼容移动端
                        var x = e.touches[0].pageX;
                    }
                    startLeft.value = Math.floor(x - barArea.value.getBoundingClientRect().left);
                    startMoveTime.value = +new Date();    //开始滑动的时间
                    if (isEnd.value == false) {
                        text.value = ''
                        moveBlockBackgroundColor.value = '#337ab7'
                        leftBarBorderColor.value = '#337AB7'
                        iconColor.value = '#fff'
                        e.stopPropagation();
                        status.value = true;
                    }
                }
                //鼠标移动
                function move(e) {
                    e = e || window.event
                    if (status.value && isEnd.value == false) {
                        if (!e.touches) {  //兼容PC端 
                            var x = e.clientX;
                        } else {           //兼容移动端
                            var x = e.touches[0].pageX;
                        }
                        var bar_area_left = barArea.value.getBoundingClientRect().left;
                        var move_block_left = x - bar_area_left //小方块相对于父元素的left值
                        if (move_block_left >= barArea.value.offsetWidth - parseInt(parseInt(blockSize.value.width) / 2) - 2) {
                            move_block_left = barArea.value.offsetWidth - parseInt(parseInt(blockSize.value.width) / 2) - 2;
                        }
                        if (move_block_left <= 0) {
                            move_block_left = parseInt(parseInt(blockSize.value.width) / 2);
                        }
                        //拖动后小方块的left值
                        var pixelLeft = move_block_left - startLeft.value
                        moveBlockLeft.value = pixelLeft + "px"
                        leftBarWidth.value = pixelLeft + "px"

                        // 按图片原始尺寸比例换算
                        var scaledLeft = pixelLeft * 310 / parseInt(setSize.imgWidth)
                        // 更新活动曲线
                        activeCtrlX = calcActiveCtrlX(scaledLeft)
                        drawCanvas()
                    }
                }

                //鼠标松开
                function end() {
                    endMovetime.value = +new Date(); 
                    //判断是否重合
                    if (status.value && isEnd.value == false) {
                        var moveLeftDistance = parseInt((moveBlockLeft.value || '').replace('px', ''));
                        moveLeftDistance = moveLeftDistance * 310/ parseInt(setSize.imgWidth)
                        // 曲线滑块: 传活动曲线控制点x坐标, y固定传0
                        let data = {
                            captchaType:captchaType.value,
                            "pointJson":secretKey.value ? aesEncrypt(JSON.stringify({x:activeCtrlX,y:0}),secretKey.value):JSON.stringify({x:activeCtrlX,y:0}),
                            "token":backToken.value
                        }
                        reqCheck(data).then(res=>{
                            if (res.repCode == "0000") {
                                moveBlockBackgroundColor.value = '#5cb85c'
                                leftBarBorderColor.value = '#5cb85c'
                                iconColor.value = '#fff'
                                iconClass.value = 'icon-check'
                                showRefresh.value = false
                                isEnd.value = true;   
                                if (mode.value=='pop') {
                                    setTimeout(()=>{
                                        proxy.$parent.clickShow = false;
                                        refresh();
                                    },1500)
                                }
                                passFlag.value = true
                                tipWords.value = `${((endMovetime.value-startMoveTime.value)/1000).toFixed(2)}s验证成功`
                                var captchaVerification = secretKey.value ? aesEncrypt(backToken.value+'---'+JSON.stringify({x:activeCtrlX,y:0}),secretKey.value):backToken.value+'---'+JSON.stringify({x:activeCtrlX,y:0})
                                setTimeout(()=>{
                                    tipWords.value = ""
                                    proxy.$parent.closeBox();
                                    proxy.$parent.$emit('success', {captchaVerification})
                                },1000)
                            }else{
                                moveBlockBackgroundColor.value = '#d9534f'
                                leftBarBorderColor.value = '#d9534f'
                                iconColor.value = '#fff'
                                iconClass.value = 'icon-close'
                                passFlag.value = false
                                setTimeout(function () {
                                    refresh();
                                }, 1000);
                                proxy.$parent.$emit('error',proxy)
                                tipWords.value = "验证失败"
                                setTimeout(()=>{
                                        tipWords.value = ""
                                },1000)
                            }
                        })
                        status.value = false;
                    }
                }

                const refresh = ()=>{
                    showRefresh.value = true
                    finishText.value = ''

                    transitionLeft.value = 'left .3s'
                    moveBlockLeft.value = 0

                    leftBarWidth.value = undefined
                    transitionWidth.value = 'width .3s'

                    leftBarBorderColor.value = '#ddd'
                    moveBlockBackgroundColor.value = '#fff'
                    iconColor.value = '#000'
                    iconClass.value = 'icon-right'
                    isEnd.value = false

                    getPictrue()
                    setTimeout(() => {
                        transitionWidth.value = ''
                        transitionLeft.value = ''
                        text.value = explain.value
                    }, 300)
                }

                // 请求背景图片和验证图片
                function getPictrue(){
                    let data = {
                        captchaType:captchaType.value
                    }
                    reqGet(data).then(res=>{
                        if (res.repCode == "0000") {
                            backImgBase.value = res.repData.originalImageBase64
                            backToken.value = res.repData.token
                            secretKey.value = res.repData.secretKey
                            // 解析曲线参数JSON
                            try {
                                curveParams = JSON.parse(res.repData.jigsawImageBase64)
                            } catch(e) {
                                console.error('曲线参数解析失败', e)
                                curveParams = null
                            }
                            // 初始活动曲线控制点x = ctrlXMin
                            activeCtrlX = curveParams ? curveParams.ctrlXMin : 0
                            // 加载底图并绘制Canvas
                            loadBgAndDraw()
                        }else{
                            tipWords.value = res.repMsg;
                        }
                    })
                }
                return {
                    secretKey,           //后端返回的ase加密秘钥
                    passFlag,         //是否通过的标识
                    backImgBase,      //验证码背景图片
                    backToken,        //后端返回的唯一token值
                    startMoveTime,    //移动开始的时间
                    endMovetime,      //移动结束的时间
                    tipsBackColor,    //提示词的背景颜色
                    tipWords,
                    text,
                    finishText,
                    setSize,
                    top,
                    left,
                    moveBlockLeft,
                    leftBarWidth,
                    // 移动中样式
                    moveBlockBackgroundColor,
                    leftBarBorderColor,
                    iconColor,
                    iconClass,
                    status,	    //鼠标状态
                    isEnd,		//是够验证完成
                    showRefresh,
                    transitionLeft,
                    transitionWidth,
                    barArea,
                    refresh,
                    start,
                    // Canvas
                    canvasRef,
                    canvasWidth,
                    canvasHeight
                }
        },
    }
</script>
