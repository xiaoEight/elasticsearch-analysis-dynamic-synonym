/**
 *
 */
package com.bellszhu.elasticsearch.plugin.synonym.analysis;

import com.bellszhu.elasticsearch.plugin.synonym.tools.SynonymFilterHandleTools;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.synonym.WordnetSynonymParser;
import org.elasticsearch.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.ParseException;

/**
 * @author bellszhu
 */
public class RemoteSynonymFile implements SynonymFile {

    private static final String LAST_MODIFIED_HEADER = "Last-Modified";
    private static final String ETAG_HEADER = "ETag";

    private static Logger logger = LogManager.getLogger("dynamic-synonym");

    private CloseableHttpClient httpclient;

    private String format;

    private boolean expand;

    private Analyzer analyzer;

    private Environment env;

    /**
     * Remote URL address
     */
    private String location;

    private String lastModified;

    private String eTags;

    private boolean extendFilter;
    private String splitSymbol;
    private boolean excludeFirst;

    RemoteSynonymFile(Environment env, Analyzer analyzer,
                      boolean expand, String format, String location,
                      boolean extendFilter,String splitSymbol,boolean excludeFirst) {
        this.analyzer = analyzer;
        this.expand = expand;
        this.format = format;
        this.env = env;
        this.location = location;

        this.httpclient = AccessController.doPrivileged((PrivilegedAction<CloseableHttpClient>) HttpClients::createDefault);

        this.extendFilter = extendFilter;
        this.splitSymbol = splitSymbol;
        this.excludeFirst = excludeFirst;

        isNeedReloadSynonymMap();
    }

    static SynonymMap.Builder getSynonymParser(Reader rulesReader, String format, boolean expand, Analyzer analyzer) throws IOException, ParseException {
        SynonymMap.Builder parser;
        if ("wordnet".equalsIgnoreCase(format)) {
            parser = new WordnetSynonymParser(true, expand, analyzer);
            ((WordnetSynonymParser) parser).parse(rulesReader);
        } else {
            parser = new SolrSynonymParser(true, expand, analyzer);
            ((SolrSynonymParser) parser).parse(rulesReader);
        }
        return parser;
    }

    @Override
    public SynonymMap reloadSynonymMap() {
        Reader rulesReader = null;
        try {
            logger.info("start reload remote synonym from {}.", location);
            rulesReader = getReader();
            SynonymMap.Builder parser;

            parser = getSynonymParser(rulesReader, format, expand, analyzer);
            return parser.build();
        } catch (Exception e) {
            logger.error("reload remote synonym {} error!", e, location);
            throw new IllegalArgumentException(
                    "could not reload remote synonyms file to build synonyms",
                    e);
        } finally {
            if (rulesReader != null) {
                try {
                    rulesReader.close();
                } catch (Exception e) {
                    logger.error("failed to close rulesReader", e);
                }
            }
        }
    }

    private CloseableHttpResponse executeHttpRequest(HttpUriRequest httpUriRequest) {
        return AccessController.doPrivileged((PrivilegedAction<CloseableHttpResponse>) () -> {
            try {
                return httpclient.execute(httpUriRequest);
            } catch (IOException e) {
                logger.error("Unable to execute HTTP request: {}", e);
            }
            return null;
        });
    }

    /**
     * Download custom terms from a remote server
     */
    public Reader getReader() {
        Reader reader = null;
        RequestConfig rc = RequestConfig.custom()
                .setConnectionRequestTimeout(10 * 1000)
                .setConnectTimeout(10 * 1000).setSocketTimeout(60 * 1000)
                .build();
        CloseableHttpResponse response;
        BufferedReader br = null;
        HttpGet get = new HttpGet(location);
        get.setConfig(rc);
        try {
            response = executeHttpRequest(get);
            if (response.getStatusLine().getStatusCode() == 200) {
                String charset = "UTF-8"; // 获取编码，默认为utf-8
                if (response.getEntity().getContentType().getValue()
                        .contains("charset=")) {
                    String contentType = response.getEntity().getContentType()
                            .getValue();
                    charset = contentType.substring(contentType
                            .lastIndexOf('=') + 1);
                }

                br = new BufferedReader(new InputStreamReader(response
                        .getEntity().getContent(), charset));
                StringBuffer sb = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null) {
                    logger.info("reload remote synonym: {}", line);
                    if(this.extendFilter){
                        line = SynonymFilterHandleTools.handleToLine(line,this.splitSymbol,this.excludeFirst);
                        logger.info("reload local synonym handle complete: {}", line);
                    }
                    sb.append(line)
                            .append(System.getProperty("line.separator"));
                }
                reader = new StringReader(sb.toString());
            }
        } catch (Exception e) {
            logger.error("get remote synonym reader {} error!", e, location);
            throw new IllegalArgumentException(
                    "Exception while reading remote synonyms file", e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                logger.error("failed to close bufferedReader", e);
            }
        }
        return reader;
    }

    @Override
    public boolean isNeedReloadSynonymMap() {
        RequestConfig rc = RequestConfig.custom()
                .setConnectionRequestTimeout(10 * 1000)
                .setConnectTimeout(10 * 1000).setSocketTimeout(15 * 1000)
                .build();
        HttpHead head = AccessController.doPrivileged((PrivilegedAction<HttpHead>) () -> new HttpHead(location));
        head.setConfig(rc);

        // 设置请求头
        if (lastModified != null) {
            head.setHeader("If-Modified-Since", lastModified);
        }
        if (eTags != null) {
            head.setHeader("If-None-Match", eTags);
        }

        CloseableHttpResponse response = null;
        try {
            response = executeHttpRequest(head);
            if (response.getStatusLine().getStatusCode() == 200) { // 返回200 才做操作
                if (!response.getLastHeader(LAST_MODIFIED_HEADER).getValue()
                        .equalsIgnoreCase(lastModified)
                        || !response.getLastHeader(ETAG_HEADER).getValue()
                        .equalsIgnoreCase(eTags)) {

                    lastModified = response.getLastHeader(LAST_MODIFIED_HEADER) == null ? null
                            : response.getLastHeader(LAST_MODIFIED_HEADER)
                            .getValue();
                    eTags = response.getLastHeader(ETAG_HEADER) == null ? null
                            : response.getLastHeader(ETAG_HEADER).getValue();
                    return true;
                }
            } else if (response.getStatusLine().getStatusCode() == 304) {
                return false;
            } else {
                logger.info("remote synonym {} return bad code {}", location,
                        response.getStatusLine().getStatusCode());
            }

        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                logger.error("failed to close http response", e);
            }
        }
        return false;
    }
}
