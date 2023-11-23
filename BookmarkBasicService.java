package com.bookmarkchina.base;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.bookmarkchina.base.bean.mysql.CopywriteProtect;
import com.bookmarkchina.base.bean.mysql.DeadLink;
import com.bookmarkchina.base.bean.mysql.UrlsWithBLOBs;
import com.bookmarkchina.base.constant.Constant;
import com.bookmarkchina.base.constant.TypeConstant;
import com.bookmarkchina.base.util.HtmlEnDecode;
import com.bookmarkchina.base.util.MD5Util;
import com.bookmarkchina.base.util.URLUtil;
import com.bookmarkchina.base.util.redis.RedisClient;
import com.bookmarkchina.dao.mysql.CopywriteProtectMapper;
import com.bookmarkchina.dao.mysql.DeadLinkMapper;
import com.bookmarkchina.dao.mysql.DownloadPrecheckMapper;
import com.bookmarkchina.dao.mysql.IllegalUrlMapper;
import com.bookmarkchina.dao.mysql.RecommendMaterialMapper;
import com.bookmarkchina.dao.mysql.UrlsMapper;
import com.bookmarkchina.module.base.util.BaseUtils;
import com.bookmarkchina.module.search.bean.IndexFolderBean;
import com.bookmarkchina.module.supervision.util.IllegalHelperUtil;
import com.bookmarkchina.module.updownload.service.IDownloadService;
import com.bookmarkchina.module.view.util.DeadUrlCheckUtil;

public abstract class BookmarkBasicService extends SearchQueryEngine<IndexFolderBean,String> {
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	protected DownloadPrecheckMapper downloadPrecheckMapper;
	
	@Autowired
	protected RecommendMaterialMapper recommendMaterialMapper;
	
	@Autowired
	private CopywriteProtectMapper copywriteProtectMapper;
	
	@Autowired
	private IllegalUrlMapper illegalUrlMapper;
	
	@Autowired
	protected IDownloadService downloadService;
	
    @Autowired
    protected RedisClient redisClient;
    
	@Autowired
	private DeadLinkMapper deadLinkMapper;
	
	@Autowired
	private UrlsMapper urlsMapper;

	
	/**
	 * 批量分析folder的违规、版权、死链接
	 * @param bookmarkId
	 * @param urlList
	 * @param removeIndexList
	 * @param onlyCheck，只做校验，不做数据修复和存储，如果为false，速度会非常的慢
	 * @return
	 */
	protected Map<String,Object> batchAnalysisFolder(Long bookmarkId,List<UrlsWithBLOBs> urlList,List<UrlsWithBLOBs> removeIndexList,Boolean onlyCheck){
			
			Map<String,Object> map=new HashMap<String,Object>();
			
			List<String> domainMd5List =new ArrayList<String>();
			List<String> urlMd5List =new ArrayList<String>();
			
			urlList.forEach((item)->{
				
				if(item!=null){
					
					String url=item.getUrl();
					//name处理
					String name=item.getName();
					if(StringUtils.isNotBlank(name)){item.setName(BaseUtils.maskEmailAndPhone(name));}
					if(url.startsWith("http")){
						try {
							String domainMd5=MD5Util.encode2hex(URLUtil.getDomainName(url));
							item.setDomainMd5(domainMd5);
							domainMd5List.add(domainMd5);
						} catch (MalformedURLException e){e.printStackTrace();}
					}
					
					String urlMd5=MD5Util.encode2hex(url);
					item.setUrlMd5(urlMd5);
					urlMd5List.add(urlMd5);

				}
			});
			
			//非法内容数量
			List<String> md5List=illegalUrlMapper.selectDomainMd5InDomainMd5List(domainMd5List);
			
			//版权内容列表
			List<CopywriteProtect> copywriteProtectList=copywriteProtectMapper.selectCopywriteProtectInUrlMd5List(urlMd5List);
			
			//死链接列表
			List<DeadLink> deadLinkList=deadLinkMapper.selectDeadLinkInUrlMd5List(urlMd5List);
			
			List<String> deadlinkMd5List=new ArrayList<String>();
			
			int illegalCount=0;
			
			if(urlList!=null){
				
				for(UrlsWithBLOBs item:urlList){
					
					if(item!=null){

						//非法内容踢除
						if(md5List.contains(item.getDomainMd5())){
							item.setUnhealthyType(TypeConstant.UNHEALTHY_TYPE_ILLEGAL);
							removeIndexList.add(item);//踢出
							illegalCount++;
						}
						
						//版权内容踢除
						if(!copywriteProtectList.isEmpty()){
							for(CopywriteProtect copywriteProtect :copywriteProtectList){//当前书签下的某个链接（bookmarkid->url）

								if(copywriteProtect.getUrlMd5()!=null&&copywriteProtect.getUrlMd5().equals(item.getUrlMd5())){

									int copywriteLevel=IllegalHelperUtil.isCopywriteContent(copywriteProtect, bookmarkId);
		  							if(copywriteLevel>0){
		  								
		  								item.setUnhealthyType(TypeConstant.UNHEALTHY_TYPE_COPYWRITE);
		  								removeIndexList.add(item);//踢出
		  	  						}
		  							
		  							if(copywriteLevel>1){
		  								illegalCount++;
		  							}
								}
							}
						}
						
						//死链接踢除
						if(!deadLinkList.isEmpty()){
							for(DeadLink deadLink:deadLinkList){
								
								String urlMd5=deadLink.getUrlMd5();
								
								if(item.getUrlMd5()!=null&&item.getUrlMd5().equals(urlMd5)){

									if(DeadUrlCheckUtil.hitRemoveThreshold(deadLink.getDayMax())){
										
										deadlinkMd5List.add(urlMd5);
										
										item.setUnhealthyType(TypeConstant.UNHEALTHY_TYPE_DEAD);
										
										removeIndexList.add(item);//内存层面移除展示
									}
								}
							}
						}
						
						//仅校验，不做数据修复
						if(!onlyCheck){
							
							if(IllegalHelperUtil.getCleanBookmark(item)==null){
								
								item.setUnhealthyType(TypeConstant.UNHEALTHY_TYPE_SENSITIVE);
								removeIndexList.add(item);//踢出
							}
							
							//处理icon、name、url
							item=IllegalHelperUtil.iconAnalysis(item);//icon 分析
							item.setName(HtmlEnDecode.htmlEncode(item.getName()));
							item.setUrl(HtmlEnDecode.htmlEncode(item.getUrl()));//url 也可能有js代码 ，顺便encode
						}
					}
				}
			}

			//仅校验，不做数据存储
			if(!deadlinkMd5List.isEmpty()){
				
				try{
					urlsMapper.batchUpateUrlState(deadlinkMd5List,Constant.STATUS_DELETE);
				}catch(Exception e){;}
				
			}
			
			map.put("removeIndexList", removeIndexList);
			map.put("hitTimes",illegalCount);
			return map;
	}
}