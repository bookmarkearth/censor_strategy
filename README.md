# censor_strategy

## 该策略记录了书签地球如何审核用户上传的书签，以及展示、搜索、完成各种色情、政治、赌博、版权内容的过滤、清洗等，考虑到书签地球的服务器安全以及产品的长久发展，避免留下被利用的算法漏洞，策略不会公示与社会或者版权方无关的技术内容，多有不便请海涵。

### 数据上传以及审核
#### 1、用户上传自己的浏览器书签到书签地球，等待机器审核
#### 2、机器会定时审核上传内容，审核步骤如下
##### （1）、解析书签结构，提取文件夹、文件夹内链接
##### （2）、依据比邻算法，将链接分类，之所以这么做是希望将有可能违规的内容筛选除去，留下干净内容
##### （3）、重点过滤版权内容，版权基础数据库可参考[https://github.com/bookmarkearth/censor_copywrite/](https://github.com/bookmarkearth/censor_copywrite/)
#####  (4) 、过滤的基础策略如下
        ```java
          	//进行疑似版权内容过滤，尽到网络服务提供商的审核义务
    				String[] splitArray = name.split("[-_——|,，]");//影视，小说等网站一般以以上字符分割
    				String checkTitle=splitArray.length>=0?splitArray[0]:name;//普遍网站都是第一段为作品名称
    				Boolean mabyeCopywrite=censorCopywriteMapper.selectNumByTitle(checkTitle.trim())>0?true:false;
        ```
#####  (5)、人工审核确认

### 数据展示
#### 1、所有展示的书签、文件夹、链接，展示前都会做色情、政治、赌博、版权内容的校验，会依据危害程度踢除、过滤、提醒等
#### 2、会在网站、app上留下明显的举报入口和举报邮箱，接受监督和帮助维权
#### 3、明确标注上传者名称、上传时间等

### 数据搜索
##### 1、爬虫代码已经开源，参考[https://github.com/bookmarkearth/bookmarkSpider](https://github.com/bookmarkearth/bookmarkSpider)
##### 2、对于违规严重的内容，爬虫是不会收录的，这里的违规严重包括：色情、政治、赌博、版权内容等
##### 3、数据索引，索引时会二次确认是否是违规内容，对疑似色情、政治、赌博、版权内容等不索引，其中版权模块的基础过滤代码如下：
       ```java
          	//进行疑似版权内容过滤，尽到网络服务提供商的审核义务
    				String[] splitArray = name.split("[-_——|,，]");//影视，小说等网站一般以以上字符分割
    				String checkTitle=splitArray.length>=0?splitArray[0]:name;//普遍网站都是第一段为作品名称
    				Boolean mabyeCopywrite=censorCopywriteMapper.selectNumByTitle(checkTitle.trim())>0?true:false;
        ```
##### 4、搜索，该过程不会对结果进行干预和设立中间页面，保持技术中立，但对于违规内容，会三次确认是否存在，如果存在则踢除、过滤、提醒等
##### 5、提供举报入口和举报邮箱，接受监督和帮助维权

        
