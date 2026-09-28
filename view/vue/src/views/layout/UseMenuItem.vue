<template>
  <div class="left-manu">
    <el-row class="tac">
      <el-col :span="24">
        <el-menu
          :default-active="activeNav"
          class="el-menu-vertical-demo"
          router
          @select="handleSelect2"
        >
          <el-submenu index="/useOnline">
            <template slot="title">
              <span>滑动验证</span>
            </template>
            <el-menu-item-group>
              <el-menu-item index="/useOnline/sliderFixed">嵌入式</el-menu-item>
              <el-menu-item index="/useOnline/sliderPop">弹出式</el-menu-item>
            </el-menu-item-group>
          </el-submenu>
          <el-submenu index="/useOnline">
            <template slot="title">
              <span>点选验证</span>
            </template>
            <el-menu-item-group>
              <el-menu-item index="/useOnline/pointFixed">嵌入式</el-menu-item>
              <el-menu-item index="/useOnline/pointPop">弹出式</el-menu-item>
            </el-menu-item-group>
          </el-submenu>
          <el-submenu index="/useOnline">
            <template slot="title">
              <span>滑块曲线验证</span>
            </template>
            <el-menu-item-group>
              <el-menu-item index="/useOnline/curveFixed">嵌入式</el-menu-item>
              <el-menu-item index="/useOnline/curvePop">弹出式</el-menu-item>
            </el-menu-item-group>
          </el-submenu>
        </el-menu>
      </el-col>
    </el-row>
  </div>
</template>
<script>
export default {
  name: 'MenuItem',
  data() {
    return {
      // activeNav:'/projectDetails'
    }
  },
  computed: {
    activeNav() {
      return this.$route.path
    }
  },
  mounted() {
    // this.activeNav=window.location.href.lastIndexOf("\\");
    // console.log(this.activeNav)
  },
  methods: {
    handleSelect2(key, keyPath) {
      // this.activeNav=key
    },
  }
}
</script>

<style scoped lang="less">
  .sidebar-container {
    transition: width 0.28s;
  .horizontal-collapse-transition {
    transition: 0s width ease-in-out, 0s padding-left ease-in-out, 0s padding-right ease-in-out;
  }
  .el-scrollbar {
    height: 100%;
  }
  .scrollbar-wrapper {
    overflow-x: hidden!important;
  .el-scrollbar__view {
    height: 100%;
  }
  }
  .el-scrollbar__bar.is-vertical{
    right: 0px;
  }
  .is-horizontal {
    display: none;
  }
  a {
    display: inline-block;
    width: 100%;
    overflow: hidden;
  }
  .el-menu {
    border: none;
    height: 100%;
    width: 100% !important;
  }
  .is-active > .el-submenu__title{
    color: #f4f4f5!important;
  }

  }

  .el-submenu__title .el-icon-arrow-down{
    line-height: 13px!important;
  }

  .el-menu {
    border-right: none;
    a {
      text-decoration: none;
    }
  }
  // div:hover,ul:hover,li:hover,span:hover,ul li:hover,ul li ul:hover{
  //   background: none!important;
  //   color: #333!important;
  // }

</style>
