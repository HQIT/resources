package com.cloume.services.resources.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.cloume.hsep.controller.base.AbstractController;
import com.cloume.hsep.rest.ErrorCodes;
import com.cloume.hsep.rest.RestClient;
import com.cloume.hsep.rest.RestResponse;
import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;

@RestController
@RequestMapping("/resources")
public class UploadController extends AbstractController {
	
	@Bean
	public BucketManager bucketManager() {
		Auth qiniuAuth = Auth.create(QINIU_ACCESS_KEY, QINIU_SECRET_KEY);
		return new BucketManager(qiniuAuth);
	}
	
	@Autowired BucketManager bucketManager;
	
	@Value("${qiniu.ak}") String QINIU_ACCESS_KEY;
	@Value("${qiniu.sk}") String QINIU_SECRET_KEY;
	@Value("${qiniu.base-url}") String QINIU_BASE_URL;
	@Value("${wx.service-url}") String WX_SERVICE_URL;

	/**
	 * 将文件上传到七牛云存储
	 * @param body
	 * {
	 *   "media": "<media indicator>",		///用来指示文件，可以是微信media id；或者文件路径等
	 *   "key": "<custom set>",						///有用户根据实际情况设置，现有user-avatar和user-id-card
	 *   "user": "<user id>"							///文件owner的id
	 * }
	 * @param provider: WX表示微信资源；FILE表示文件；URL表示www地址
	 * @return { code, message, result }
	 * 其中result包含hash/key/base_url三个属性. base_url + "/" + key就是获取图片的地址
	 */
	@RequestMapping(value = "/upload", method = RequestMethod.PUT, params = { "provider" })
	public RestResponse<?> upload(@RequestBody Map<String, Object> body, String provider) {
		final String[] fields = new String[]{ "media", "key", "user"};
		if(!verify(body, Arrays.asList(fields))){
			return RestResponse.bad(ErrorCodes.EC_MISSING_PARAMETERS, String.format("missing parameters: %s", StringUtils.join(fields, ',')), null);
		}
		
		String media = String.valueOf(body.get("media"));
		String bucketName = String.valueOf(body.get("key"));
		String user = String.valueOf(body.get("user"));
		
		switch(provider.toLowerCase()) {
		case "wx": {
			RestResponse<?> mediaUrl = null;
			try {
				mediaUrl = new RestClient().get(WX_SERVICE_URL + "/media/url?mediaId=" + media + "&media=" + media);
			} catch (IOException e) {
				return RestResponse.bad(ErrorCodes.EC_BAD, "Faild to call api /media/url", e);
			}
			String wxMediaUrl = String.valueOf(mediaUrl.getResult());
			body.put("media", wxMediaUrl);
			return upload(body, "URL");
		} 
		case "url": {
			DefaultPutRet result = null;
			try {
				result = bucketManager.fetch(media, bucketName, user);
			} catch (QiniuException e) {
				return RestResponse.bad(ErrorCodes.EC_BAD, "Faild to fetch wx resource by qiniu", e);
			}
			
			///通过自定义域名result.key可以获取外链url
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put("hash", result.hash);
			payload.put("key", result.key);
			payload.put("base_url", QINIU_BASE_URL);
			
			return RestResponse.good(payload);
		}
		case "file": {
			
		} break;
		default: {}
		}
		
		return RestResponse.bad(ErrorCodes.EC_INVALID_VALUE, "un-supported provider", null);
	}
}
