package com.bellszhu.elasticsearch.plugin.synonym.tools;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class SynonymFilterHandleTools {

    private static Logger logger = LogManager.getLogger("dynamic-synonym-filter-tool");

    public static String handleToLine(String wordsStr,String splitSymbol,boolean excludeFirst){
        StringBuilder sb = new StringBuilder();
        List<String> list = handle(wordsStr,splitSymbol,excludeFirst);
        for(int i=0;i<list.size();i++){
            sb.append(list.get(i));
            if(i < (list.size() - 1)){
                sb.append(splitSymbol);
            }
        }
        return sb.toString();
    }

    public static List<String> handle(String wordsStr,String splitSymbol,boolean excludeFirst){
        List<String> words = Lists.newArrayList(wordsStr.split(splitSymbol));
        String firstWord = null;
        if(excludeFirst){
            firstWord = words.get(0);
            words.remove(0);
        }

        words.addAll(tokenFilter(words, tokenStream -> new BigramFilter(tokenStream)));
        words = tokenFilter(words, tokenStream -> new PyFilter(tokenStream,false,true,true,"lower"));
        if(excludeFirst){
            words.add(0,firstWord);
        }
        return words;
    }

    public static List<String> tokenFilter(List<String> words, Function<TokenStream,TokenFilter> createTokenFilterFunc){
        List<String> result = Lists.newArrayList();
        for(String word:words){
            TokenStream ts = new KeywordAnalyzer().tokenStream("", new StringReader(word));
            TokenFilter tokenFilter = createTokenFilterFunc.apply(ts);
            try {
                tokenFilter.reset();
                CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
                while (tokenFilter.incrementToken()) {
                    result.add(termAtt.toString());
                }
            }catch (IOException e){
                logger.error(e.getMessage(),e);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        String v = "AIA007001C, 同系统";
        for(String vv:handle(v,", ",false)){
            System.out.println(vv);
        }

        System.out.println(handleToLine(v,", ",false));
    }

}
