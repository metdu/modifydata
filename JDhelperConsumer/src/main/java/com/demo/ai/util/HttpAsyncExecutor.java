package com.demo.ai.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.client.HttpAsyncClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.synchronoss.cloud.nio.multipart.util.IOUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class HttpAsyncExecutor {
    private WebClient webClient;
    protected final static Logger logger = Logger.getLogger(HttpAsyncExecutor.class.getSimpleName());
    /** json转换为对象的mapper */
    private static ObjectMapper defaultMapper = new ObjectMapper();
    /** 是否需要打印请求详细信息 */
    private static boolean httpPrintDetail;

    static {
        defaultMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        defaultMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    }

    @Autowired
    public HttpAsyncExecutor(HttpAsyncClient asyncClient) {
//        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(options -> {
//            options.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 70000)
//                    .onChannelInit(channel -> {
////                            channel.pipeline().addLast(new ReadTimeoutHandler(7));
//                        return true;
//                    }).build();
//        });
//        ReactorClientHttpConnector connector = new ReactorClientHttpConnector();
        HttpClient httpClient = HttpClient.create();
        httpClient.compress(true);
        httpClient.tcpConfiguration(client ->
                client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .doOnConnected(conn ->
                                conn.addHandlerLast(new ReadTimeoutHandler(8))
                                        .addHandlerLast(new WriteTimeoutHandler(8))).noProxy());
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        this.webClient = WebClient.builder()
                .clientConnector(connector)
                .build();
    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     */
    public void getString(String url, Consumer<String> callback, Consumer<Throwable> onError) {
        getString(url, null, callback, onError, null);
    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     */
    public void getString(String url, List<Map.Entry<String,String>> headers, Consumer<String> callback, Consumer<Throwable> onError, final Charset respCharset) {
        HttpGet req = new HttpGet(url);

        if(headers != null) {
            headers.forEach(entry -> req.setHeader(entry.getKey(), entry.getValue()));
        }

        logger.info("url = " + url);

        executeString(req, callback, onError, respCharset);
    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param clazz 接收数据的类型
     * @param objectMapper json转实例的mapper
     * @param respCharset 指定的返回数据charset
     * @param <T>
     */
    public <T> void getObject(String url, List<Map.Entry<String,String>> headers, Consumer<T> callback, Consumer<Throwable> onError, Class<T> clazz, final ObjectMapper objectMapper, final Charset respCharset) {
        HttpGet req = new HttpGet(url);

        if(headers != null) {
            headers.forEach(entry -> req.setHeader(entry.getKey(), entry.getValue()));
        }

        logger.info("url = " + url);

        executeObject(req, callback, onError, clazz, objectMapper, respCharset);
    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param formData 请求内容
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param respCharset 返回数据的charset
     */
    public void postString(String url, Map<String,String> formData, List<Map.Entry<String,String>> headers, Consumer<String> callback, Consumer<Throwable> onError, Charset respCharset) {
        List<NameValuePair> params = new ArrayList<>(formData.size());
        formData.forEach((k,v) -> params.add(new BasicNameValuePair(k, v)));

        HttpEntity entity = new UrlEncodedFormEntity(params, StandardCharsets.UTF_8);

        postString(url, entity, headers, callback, onError, respCharset);

    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param content 请求内容
     * @param contentType contentType
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param respCharset 返回数据的charset
     */
    public void postString(String url, String content, ContentType contentType, List<Map.Entry<String,String>> headers, Consumer<String> callback, Consumer<Throwable> onError, final Charset respCharset) {

        try {
            HttpEntity entity = new StringEntity(content, contentType);

            postString(url, entity, headers, callback, onError, respCharset);
        } catch (UnsupportedCharsetException e) {
            e.printStackTrace();
        }

    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param entity 请求内容
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param respCharset 返回数据的charset
     */
    public void postString(String url, HttpEntity entity, List<Map.Entry<String,String>> headers, Consumer<String> callback, Consumer<Throwable> onError, final Charset respCharset) {
        HttpPost req = new HttpPost(url);

        req.setEntity(entity);

        if(headers != null) {
            headers.forEach(entry -> req.setHeader(entry.getKey(), entry.getValue()));
        }

        executeString(req, callback, onError, respCharset);
    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param formData 请求内容
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param clazz 接收数据的类型
     * @param objectMapper json转实例的mapper
     * @param respCharset 指定的返回数据charset
     * @param <T>
     */
    public <T> void postObject(String url, Map<String,String> formData, List<Map.Entry<String,String>> headers, Consumer<T> callback, Consumer<Throwable> onError, Class<T> clazz, final ObjectMapper objectMapper, Charset respCharset) {
        List<NameValuePair> params = new ArrayList<>(formData.size());
        formData.forEach((k,v) -> params.add(new BasicNameValuePair(k, v)));

        HttpEntity entity = new UrlEncodedFormEntity(params, StandardCharsets.UTF_8);

        postObject(url, entity, headers, callback, onError, clazz, objectMapper, respCharset);

    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param content 请求内容
     * @param contentType contentType
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param clazz 接收数据的类型
     * @param objectMapper json转实例的mapper
     * @param respCharset 指定的返回数据charset
     * @param <T>
     */
    public <T> void postObject(String url, String content, ContentType contentType, List<Map.Entry<String,String>> headers, Consumer<T> callback, Consumer<Throwable> onError, Class<T> clazz, final ObjectMapper objectMapper, final Charset respCharset) {

        try {
            HttpEntity entity = new StringEntity(content, contentType);

            postObject(url, entity, headers, callback, onError, clazz, objectMapper, respCharset);
        } catch (UnsupportedCharsetException e) {
            e.printStackTrace();
        }

    }

    /**
     * 访问网址，获取数据
     * @param url 网址
     * @param entity 请求内容
     * @param headers 请求头
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param clazz 接收数据的类型
     * @param objectMapper json转实例的mapper
     * @param respCharset 指定的返回数据charset
     * @param <T>
     */
    public <T> void postObject(String url, HttpEntity entity, List<Map.Entry<String,String>> headers, Consumer<T> callback, Consumer<Throwable> onError, Class<T> clazz, final ObjectMapper objectMapper, final Charset respCharset) {
        HttpPost req = new HttpPost(url);

        req.setEntity(entity);

        if(headers != null) {
            headers.forEach(entry -> req.setHeader(entry.getKey(), entry.getValue()));
        }

        executeObject(req, callback, onError, clazz, objectMapper, respCharset);
    }

    /**
     * 访问网址，获取数据
     * @param req 访问请求
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param respCharset 指定的返回数据charset
     */
    private void executeString(HttpUriRequest req, Consumer<String> callback, Consumer<Throwable> onError, final Charset respCharset) {
        logger.info("start executing request: " + req);

        HttpMethod method = HttpMethod.valueOf(req.getMethod());
        URI uri = req.getURI();

        try {
            long start = System.currentTimeMillis();
            WebClient.RequestBodySpec requestBodySpec = webClient.method(method)
                    .uri(uri)
                    .headers(header->{
                        Header[] oldHeaders = req.getAllHeaders();
                        if (oldHeaders != null && oldHeaders.length > 0) {
                            for (Header temp : oldHeaders) {
                                header.add(temp.getName(), temp.getValue());
                            }
                        }
                    });
            logger.info("executeString time:"+(System.currentTimeMillis()-start));
            String reqBody;
            if (req instanceof HttpEntityEnclosingRequestBase) {
                HttpEntity httpEntity = ((HttpEntityEnclosingRequestBase) req).getEntity();

                requestBodySpec.contentType(MediaType.valueOf(httpEntity.getContentType().getValue()));
                reqBody = IOUtils.inputStreamAsString(httpEntity.getContent(), StandardCharsets.UTF_8.name());
                requestBodySpec.syncBody(reqBody);
            } else {
                reqBody = null;
            }

            Mono<String> result = requestBodySpec
                    .retrieve()
                    .bodyToMono(String.class);
            logger.info("executeString time2:"+(System.currentTimeMillis()-start));
            result.subscribe(respBody->{
                logger.info("executeString time3:"+(System.currentTimeMillis()-start));
                callback.accept(respBody);
                logger.info("executeString time4:"+(System.currentTimeMillis()-start));
                // 打印详细的请求信息
                if (httpPrintDetail) {
                    StringBuilder sbu = new StringBuilder()
                            .append("http request uri:").append(uri.toString()).append("\n")
                            .append("request body:").append(reqBody).append("\n")
                            .append("response body:").append(respBody);
                    logger.info(sbu.toString());
                }
            }, e->{
                onError.accept(e);
                // 打印详细的请求信息
                if (httpPrintDetail) {
                    StringBuilder sbu = new StringBuilder()
                            .append("http error request uri:").append(uri.toString()).append("\n")
                            .append("request body:").append(reqBody).append("\n")
                            .append("response error:");
                    //logger.info(sbu.toString(), e);
                }
            });
        } catch (Exception e) {
            onError.accept(e);
            // 打印详细的请求信息
            if (httpPrintDetail) {
                StringBuilder sbu = new StringBuilder()
                        .append("http error request uri:").append(uri.toString()).append("\n")
                        .append("http request error:");
              //  logger.info(sbu.toString(), e);
            }
        }

    }

    /**
     * 访问网址，获取数据
     * @param req 访问请求
     * @param callback 访问成功时的回调
     * @param onError 出错时的回调
     * @param clazz 接收数据的类型
     * @param srcObjectMapper json转实例的mapper
     * @param respCharset 指定的返回数据charset
     * @param <T>
     */
    private <T> void executeObject(HttpUriRequest req, Consumer<T> callback, Consumer<Throwable> onError, Class<T> clazz, final ObjectMapper srcObjectMapper, final Charset respCharset) {
        logger.info("start executing request: " + req);

        HttpMethod method = HttpMethod.valueOf(req.getMethod());
        URI uri = req.getURI();

        try {
            WebClient.RequestBodySpec requestBodySpec = webClient.method(method)
                    .uri(uri)
                    .headers(header->{
                        Header[] oldHeaders = req.getAllHeaders();
                        if (oldHeaders != null && oldHeaders.length > 0) {
                            for (Header temp : oldHeaders) {
                                header.add(temp.getName(), temp.getValue());
                            }
                        }
                    });
            final String reqBody;
            if (req instanceof HttpEntityEnclosingRequestBase) {
                HttpEntity httpEntity = ((HttpEntityEnclosingRequestBase) req).getEntity();

                requestBodySpec.contentType(MediaType.valueOf(httpEntity.getContentType().getValue()));
                reqBody = IOUtils.inputStreamAsString(httpEntity.getContent(), "UTF-8");
                requestBodySpec.syncBody(reqBody);
            } else {
                reqBody = null;
            }

            // use optional ObjectMapper
            final ObjectMapper objectMapper;
            if (srcObjectMapper == null) {
                objectMapper = defaultMapper;
            } else {
                objectMapper = srcObjectMapper;
            }

            Mono<String> result = requestBodySpec
                    .retrieve()
                    .bodyToMono(String.class);

            result.subscribe(respBody->{
                try {
                    callback.accept(objectMapper.readValue(respBody, clazz));
                    // 打印详细的请求信息
                    if (httpPrintDetail) {
                        StringBuilder sbu = new StringBuilder()
                                .append("http request uri:").append(uri.toString()).append("\n")
                                .append("request body:").append(reqBody).append("\n")
                                .append("response body:").append(respBody);
                        logger.info(sbu.toString());
                    }
                } catch (IOException e) {
                    onError.accept(e);
                    // 打印详细的请求信息
                    if (httpPrintDetail) {
                        StringBuilder sbu = new StringBuilder()
                                .append("http error request uri:").append(uri.toString()).append("\n")
                                .append("request body:").append(reqBody).append("\n")
                                .append("response error:");
                     //   logger.info(sbu.toString(), e);
                    }
                }
            }, e->{
                onError.accept(e);
                // 打印详细的请求信息
                if (httpPrintDetail) {
                    StringBuilder sbu = new StringBuilder()
                            .append("http error request uri:").append(uri.toString()).append("\n")
                            .append("request body:").append(reqBody).append("\n")
                            .append("response error:");
                 //   logger.info(sbu.toString(), e);
                }
            });


        } catch (Exception e) {
            onError.accept(e);
            // 打印详细的请求信息
            if (httpPrintDetail) {
                StringBuilder sbu = new StringBuilder()
                        .append("http error request uri:").append(uri.toString()).append("\n")
                        .append("http request error:");
              //  logger.info(sbu.toString(), e);
            }
        }

    }

    /**
     * 从response中获取Charset
     * @param respEntity 返回的response
     * @param defaultCharset 默认的charset
     * @return Content-Type指定的charset。如果获取不到，返回默认的charset
     */
    private static Charset getCharsetFromResponse(HttpEntity respEntity, Charset defaultCharset) {
        Charset ret = defaultCharset;
        try {
            ContentType contentType = ContentType.get(respEntity);
            if (contentType != null && contentType.getCharset() != null) {
                ret = contentType.getCharset();
            }
        } catch (Exception ignored) {
        }
        return ret;
    }

/*    public static void main(String[] argv) throws InterruptedException {
        HttpAsyncExecutor test = new HttpAsyncExecutor(null);
        test.getString("http://localhost:25007/train/test", s->{
            try {
                System.out.println("aaaaaaaaaaa " + s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, null);
        Thread.sleep(2000L);
        System.out.println("1111");
    }*/

}
