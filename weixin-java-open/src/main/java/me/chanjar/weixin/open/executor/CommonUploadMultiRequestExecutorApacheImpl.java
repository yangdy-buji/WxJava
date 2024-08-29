package me.chanjar.weixin.open.executor;

import lombok.Getter;
import me.chanjar.weixin.common.bean.CommonUploadData;
import me.chanjar.weixin.open.bean.CommonUploadMultiParam;
import me.chanjar.weixin.common.bean.CommonUploadParam;
import me.chanjar.weixin.common.enums.WxType;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.common.util.http.RequestHttp;
import me.chanjar.weixin.common.util.http.apache.Utf8ResponseHandler;
import org.apache.http.Consts;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author xzh
 * @Description
 * @createTime 2024/08/14 16:28
 */
public class CommonUploadMultiRequestExecutorApacheImpl extends CommonUploadMultiRequestExecutor<CloseableHttpClient, HttpHost> {

  public CommonUploadMultiRequestExecutorApacheImpl(RequestHttp<CloseableHttpClient, HttpHost> requestHttp) {
    super(requestHttp);
  }

  @Override
  public String execute(String uri, CommonUploadMultiParam param, WxType wxType) throws WxErrorException, IOException {
    HttpPost httpPost = new HttpPost(uri);
    if (requestHttp.getRequestHttpProxy() != null) {
      RequestConfig config = RequestConfig.custom().setProxy(requestHttp.getRequestHttpProxy()).build();
      httpPost.setConfig(config);
    }
    if (param != null) {
      MultipartEntityBuilder entity = MultipartEntityBuilder.create();

      List<CommonUploadMultiParam.NormalParam> normalParams = param.getNormalParams();
      if (!CollectionUtils.isEmpty(normalParams)) {
        for (CommonUploadMultiParam.NormalParam normalParam : normalParams) {
          entity.addPart(normalParam.getName(), new StringBody(normalParam.getValue(), ContentType.create("multipart/form-data", Consts.UTF_8)));
        }
      }

      CommonUploadParam uploadParam = param.getUploadParam();
      if (uploadParam != null) {
        CommonUploadData data = uploadParam.getData();
        InnerStreamBody part = new InnerStreamBody(data.getInputStream(), ContentType.DEFAULT_BINARY, data.getFileName(), data.getLength());
        entity.addPart(uploadParam.getName(), part)
          .setMode(HttpMultipartMode.RFC6532);
      }

      httpPost.setEntity(entity.build());
    }
    try (CloseableHttpResponse response = requestHttp.getRequestHttpClient().execute(httpPost)) {
      String responseContent = Utf8ResponseHandler.INSTANCE.handleResponse(response);
      if (responseContent == null || responseContent.isEmpty()) {
        throw new WxErrorException(String.format("上传失败，服务器响应空 url:%s param:%s", uri, param));
      }
      WxError error = WxError.fromJson(responseContent, wxType);
      if (error.getErrorCode() != 0) {
        throw new WxErrorException(error);
      }
      return responseContent;
    } finally {
      httpPost.releaseConnection();
    }
  }

  /**
   * 内部流 请求体
   */
  @Getter
  public static class InnerStreamBody extends InputStreamBody {

    private final long contentLength;

    public InnerStreamBody(final InputStream in, final ContentType contentType, final String filename, long contentLength) {
      super(in, contentType, filename);
      this.contentLength = contentLength;
    }
  }
}
