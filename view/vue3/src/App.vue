<template>
  <button @click="onShow('blockPuzzle')">滑块</button>
  <button @click="onShow('clickWord')">点击文字</button>
  <button @click="onShow('curveSlider')">滑块曲线</button>
  <Verify
      mode="pop"
      :captchaType="captchaType"
      :imgSize="{width:'400px',height:'200px'}"
      ref="verify"
    ></Verify>
</template>

<script>
import { ref,getCurrentInstance} from 'vue'
import Verify from '@/components/verifition/Verify'
export default {
  name: 'App',
  components: {
    Verify
  },
  setup(props,context){
    const verify = ref(null)
    const captchaType = ref('')
    const onShow = (type)=>{
      captchaType.value = type
      verify.value.show()
    }
    const handleSuccess = (res)=>{
      console.log(res);
      console.log('sucess');
    }
    return {
      verify,
      handleSuccess,
      onShow,
      captchaType
    }
    
  }
}
</script>

<style lang="less">
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
