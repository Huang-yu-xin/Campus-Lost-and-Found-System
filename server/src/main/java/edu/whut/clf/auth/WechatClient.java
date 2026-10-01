package edu.whut.clf.auth;

import com.fasterxml.jackson.databind.JsonNode;
import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信 code2session：用小程序临时 code 换取 openid。
 * AppSecret 仅存服务端配置（application-local.yml / deploy/.env，均 gitignored）。
 * 未配置凭据时抛 WECHAT_LOGIN_UNAVAILABLE，由测试登录兜底。
 */
@Component
public class WechatClient {

    private final AppProperties props;
    private final RestClient restClient;

    public WechatClient(AppProperties props) {
        this.props = props;
        // D3(P2-3)：外呼设置连接/读取超时各 3s，避免微信侧慢响应长时间占用线程/连接
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return notBlank(props.getWechat().getAppid()) && notBlank(props.getWechat().getAppsecret());
    }

    /** 返回微信 openid。 */
    public String code2session(String jsCode) {
        if (!isConfigured()) {
            throw BusinessException.of(ErrorCode.WECHAT_LOGIN_UNAVAILABLE);
        }
        if (!notBlank(jsCode)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺少登录凭证 code");
        }
        // D3：用 UriComponentsBuilder 编码查询参数，避免 code 中特殊字符破坏 URL
        String url = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", props.getWechat().getAppid())
                .queryParam("secret", props.getWechat().getAppsecret())
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .encode()
                .toUriString();
        JsonNode body;
        try {
            body = restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.WECHAT_LOGIN_UNAVAILABLE);
        }
        if (body == null || body.hasNonNull("errcode") && body.get("errcode").asInt() != 0) {
            // 不回传微信原始错误明文给客户端
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_UNAVAILABLE, "微信登录校验失败");
        }
        String openid = body.hasNonNull("openid") ? body.get("openid").asText() : null;
        if (!notBlank(openid)) {
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_UNAVAILABLE, "未获取到微信标识");
        }
        return openid;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
