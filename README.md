# Utopia TTS Rebirth
这是一个系统级的语音合成软件。可以在系统中将本TTS设置为默认TTS以供第三方软件比如开源阅读等使用，同时也支持应用内生成音频文件。本项目为[Utopia TTS](https://github.com/UtopiaXC/UtopiaTTS)的重生版。  

# 使用
在[Release](https://github.com/UtopiaXC/UtopiaTTS-Rebirth/releases/)中下载并安装软件。在音色配置中选择模型并进行其他配置。如果使用Azure还需要在设置页添加密钥。  
## 系统TTS
### 替换系统TTS
不同安卓系统有些许差异，以下为原生安卓16的修改方式，其他机型请自行搜索：  
打开系统设置→无障碍功能→常规 文字转语音输出→首选引擎选择UtopiaTTS
### 第三方应用内替换TTS
部分第三方应用选择TTS引擎时，可不使用系统TTS，可以自行选择引擎，请根据具体第三方应用不同策略进行修改，以下以开源阅读为例：  
打开开源阅读→随便选一本小说打开→点击屏幕中间唤出底栏并点击底部朗读按钮→点击屏幕中间唤出底栏并点击设置→点击朗读引擎→选中UtopiaTTS并点击全局→返回即可生效
## 应用内音频生成
### 文本模式
不要尝试一次性合成过长的文本，可能出现未知Bug。  
### SSML模式
请使用[微软标准SSML格式](https://learn.microsoft.com/zh-cn/azure/ai-services/speech-service/speech-synthesis-markup-structure)，如格式错误会合成失败。  
### 合成历史
可以在应用内语音合成页面右上角查看所有合成历史。如果希望修改保存的历史条目可以在设置中修改。  
# 支持的模型
目前仅适配微软TTS，支持Edge Web Socket接口与自己添加微软Azure Key。  
Edge Web Socket可无限制免费试用，Azure免费版目前每月五十万字符额度，收费层级具体请参考[Azure TTS](https://azure.microsoft.com/en-us/pricing/details/speech/)。  
  
如有其他模型需要请提交issue。  

# 测试环境
文石Leaf5 内置Neo Reader  
Xperia 1 Ⅱ 开源阅读

# AI编码与开源
本软件使用Vibecode+人工审核的方式开发。  
采用模型：Claude Opus 4.6与Gemini 3.5 Flash。  
采用MIT开源，如认定本仓库存在AI侵权行为请通过GitHub官方发送DMCA函。  

# 捐助
请不要在任何渠道以任何方式为本项目付出金钱。  
如果您想捐助本项目，您可以向慈善组织或开放源代码促进会（开源组织，OSI）捐款，我们会感激不尽。  

# 演示视频
演示使用文石Leaf5电子书录制，部分UI可能由于电子书系统强制修改导致和普通安卓设备不一致，请以具体平台内显示为准。  
## 第三方APP调用


https://github.com/user-attachments/assets/8715ef40-4735-474d-a957-88ba90c8d4d9


## APP内合成


https://github.com/user-attachments/assets/036aea87-0fdf-4c37-85af-379115565ab9

