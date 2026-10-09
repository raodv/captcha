
# **在线体验暂时下线 ！！！**

[//]: # (#### &emsp; 在线文档:[https://ajcaptcha.beliefteam.cn/captcha-doc/]&#40;https://ajcaptcha.beliefteam.cn/captcha-doc/ "doc"&#41;<br>)

[//]: # ([![License]&#40;https://img.shields.io/badge/license-Apache%202-4EB1BA.svg&#41;]&#40;https://www.apache.org/licenses/LICENSE-2.0.html&#41;[![Total Lines]&#40;https://tokei.rs/b1/github/anji-plus/captcha?category=lines&#41;]&#40;https://github.com/anji-plus/captcha&#41;)

[//]: # ()
[//]: # ([![EN doc]&#40;https://img.shields.io/badge/document-English-blue.svg&#41;]&#40;README.md&#41;[![CN doc]&#40;https://img.shields.io/badge/文档-中文版-blue.svg&#41;]&#40;README_CN.md&#41;)

## 简介
&emsp; &emsp; AJ-Captcha行为验证码，包含滑动拼图、滑块曲线、文字点选三种方式，UI支持弹出和嵌入两种方式。后端提供Java、Golang实现，前端提供了php、angular、html、vue、uni-app、flutter、android、ios等代码示例。

# 1 在线体验
**在线体验暂时下线**

# 2 功能概述
#### &emsp; 2.1 组件介绍
&emsp;&emsp; 行为验证码采用嵌入式集成方式，接入方便，安全，高效。抛弃了传统字符型验证码展示-填写字符-比对答案的流程，采用验证码展示-采集用户行为-分析用户行为流程，用户只需要产生指定的行为轨迹，不需要键盘手动输入，极大优化了传统验证码用户体验不佳的问题；同时，快速、准确的返回人机判定结果。目前对外提供三种类型的验证码，包含滑动拼图、滑块曲线、文字点选。如图1-1、1-2、1-3所示。若希望不影响原UI布局，可采用弹出式交互。<br>
&emsp;&emsp; 后端基于Java实现，提供纯Java.jar和SpringBoot Starter。前端提供了Android、iOS、Futter、Uni-App、ReactNative、Vue、Angular、Html、Php等多端示例。<br>


| 滑动拼图 | 滑块曲线 | 文字点选 |
| --- | --- | --- |
|![滑动拼图](images/%E6%BB%91%E5%8A%A8%E6%8B%BC%E5%9B%BE.gif "滑动拼图")&emsp;|![滑块曲线](images/%E6%BB%91%E5%9D%97%E6%9B%B2%E7%BA%BF.gif "滑块曲线")&emsp;|![点选文字](images/%E7%82%B9%E9%80%89%E6%96%87%E5%AD%97.gif "点选文字")|
| 图1-1 | 图1-2 | 图1-3 |
 <br>


#### &emsp; 2.2 概念术语描述
| 术语  | 描述  |
| ------------ | ------------ |
| 验证码类型 | 1）滑动拼图 blockPuzzle  2）滑块曲线 curveSlider  3）文字点选 clickWord|
| 验证  |  用户拖动/点击一次验证码拼图即视为一次“验证”，不论拼图/点击是否正确 |
| 二次校验  | 验证数据随表单提交到后台后，后台需要调用captchaService.verification做二次校验。目的是核实验证数据的有效性。  |

# 3 交互流程
①	用户访问应用页面，请求显示行为验证码<br>
②	用户按照提示要求完成验证码拼图/点击<br>
③	用户提交表单，前端将第二步的输出一同提交到后台<br>
④	验证数据随表单提交到后台后，后台需要调用captchaService.verification做二次校验。<br>
⑤	第4步返回校验通过/失败到产品应用后端，再返回到前端。如下图所示。
![时序图](view/vue/static/shixu.png "时序图")

# 4 目录结构
├─core<br>
│&emsp;├─captcha   &emsp;&emsp;    java核心源码<br>
│&emsp;└─captcha-spring-boot-starter    &emsp;&emsp;    springboot快速启动<br>
├─images    &emsp;&emsp;&emsp;&emsp;&emsp;    效果图<br>
├─service<br>
│&emsp;├─go   &emsp;&emsp;    后端为go项目示例<br>
│&emsp;├─php   &emsp;&emsp;    后端为php项目示例<br>
│&emsp;├─go   &emsp;&emsp;    后端为go项目示例<br>
│&emsp;├─springboot   &emsp;&emsp;    后端为springboot-2.x项目示例<br>
│&emsp;├─springboot3   &emsp;&emsp;    后端为springboot-3.x项目示例<br>
│&emsp;└─springmvc    &emsp;&emsp;    后端为springmvc非springboot项目示例<br>
└─view    &emsp;&emsp;&emsp;&emsp;&emsp;    多语言客户端示例<br>
&emsp;├─android    &emsp;&emsp;    原生android实现示例<br>
&emsp;├─angular    &emsp;&emsp;    angular实现示例<br>
&emsp;├─flutter    &emsp;&emsp;    flutter实现示例<br>
&emsp;├─html    &emsp;&emsp;    原生html实现示例<br>
&emsp;├─ios    &emsp;&emsp;    原生ios实现示例<br>
&emsp;├─php    &emsp;&emsp;    php实现示例<br>
&emsp;├─react    &emsp;&emsp;    react实现示例<br>
&emsp;├─uni-app    &emsp;&emsp;    uni-app实现示例<br>
&emsp;├─wx-applet    &emsp;&emsp;    微信小程序实现示例<br>
&emsp;└─vue    &emsp;&emsp;    vue实现示例<br>
&emsp;

# 5 接入文档
#### &emsp; 5.1 本地启动
&emsp; 第一步，启动后端，导入Eclipse或者Intellij,启动service/springboot的StartApplication。[社区底图库](https://gitee.com/anji-plus/AJ-Captcha-Images)<br>
&emsp; 第二步，启动前端，使用visual code打开文件夹view/vue，npm install后npm run dev，浏览器登录<br>
```js
npm install
npm run dev

DONE  Compiled successfully in 29587ms                       12:06:38
I  Your application is running here: http://localhost:8081
``` 
&emsp;详细的前后端接入文档，后端示例代码service目录下，前端示例代码view目录下。

# 6 技术支持，如有问题，请提交Issue
提交Issue前请查阅已有Issue.
如有兴趣提交PR,请参考[提交PR规范](./CONTRIBUTING.md),切忌不要直接提交大PR，否则会影响合并流程和审核时效，也不要提交到master分支。

[//]: # (技术交流群：新群可直接进<br>)

[//]: # (<img src="https://ajreport.beliefteam.cn/file/download/495faaaa-4277-45e8-979a-ffccb6b1e86f" width = "200" height = auto />)

加微信前请备注AJ-Captcha。
<br>微信群地址

<img src="https://foruda.gitee.com/images/1724817510954363329/305a6044_7492051.jpeg" width = "200" height = auto />

#### 开源不易，劳烦各位star ☺

<br>

# Stargazers over time
[![Stargazers over time](https://starchart.cc/anji-plus/captcha.svg)](https://starchart.cc/anji-plus/captcha)