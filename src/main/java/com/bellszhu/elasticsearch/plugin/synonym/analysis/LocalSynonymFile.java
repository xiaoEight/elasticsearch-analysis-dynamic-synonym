/**
 *
 */
package com.bellszhu.elasticsearch.plugin.synonym.analysis;

import com.bellszhu.elasticsearch.plugin.synonym.tools.SynonymFilterHandleTools;
import org.apache.commons.codec.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.elasticsearch.env.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;


/**
 * @author bellszhu
 */
public class LocalSynonymFile implements SynonymFile {

    private static Logger logger = LogManager.getLogger("dynamic-synonym");

    private String format;

    private boolean expand;

    private Analyzer analyzer;

    private Environment env;

    /**
     * Local file path relative to the config directory
     */
    private String location;

    private Path synonymFilePath;

    private long lastModified;

    private boolean extendFilter;
    private String splitSymbol;
    private boolean excludeFirst;

    LocalSynonymFile(Environment env, Analyzer analyzer, boolean expand,
                     String format, String location,
                     boolean extendFilter,String splitSymbol,boolean excludeFirst) {
        this.analyzer = analyzer;
        this.expand = expand;
        this.format = format;
        this.env = env;
        this.location = location;

        this.synonymFilePath = env.configFile().resolve(location);

        this.extendFilter = extendFilter;
        this.splitSymbol = splitSymbol;
        this.excludeFirst = excludeFirst;

        isNeedReloadSynonymMap();
    }

    @Override
    public SynonymMap reloadSynonymMap() {
        try {
            logger.info("start reload local synonym from {}.", location);
            Reader rulesReader = getReader();
            SynonymMap.Builder parser = RemoteSynonymFile.getSynonymParser(rulesReader, format, expand, analyzer);
            return parser.build();
        } catch (Exception e) {
            logger.error("reload local synonym {} error!", e, location);
            throw new IllegalArgumentException(
                    "could not reload local synonyms file to build synonyms", e);
        }

    }

    public Reader getReader() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                synonymFilePath.toUri().toURL().openStream(), Charsets.UTF_8))) {
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                logger.info("reload local synonym: {}", line);
                if(this.extendFilter){
                    line = SynonymFilterHandleTools.handleToLine(line,splitSymbol,excludeFirst);
                    logger.info("reload local synonym handle complete: {}", line);
                }
                sb.append(line).append(System.getProperty("line.separator"));
            }
            return new StringReader(sb.toString());
        } catch (IOException e) {
            logger.error("get local synonym reader {} error!", e, location);
            throw new IllegalArgumentException(
                    "IOException while reading local synonyms file", e);
        }
    }

    @Override
    public boolean isNeedReloadSynonymMap() {
        try {
            File synonymFile = synonymFilePath.toFile();
            if (synonymFile.exists()
                    && lastModified < synonymFile.lastModified()) {
                lastModified = synonymFile.lastModified();
                return true;
            }
        } catch (Exception e) {
            logger.error("check need reload local synonym {} error!", e,
                    location);
        }

        return false;
    }

}