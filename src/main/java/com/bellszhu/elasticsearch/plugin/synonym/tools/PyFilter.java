package com.bellszhu.elasticsearch.plugin.synonym.tools;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.ESLoggerFactory;

public class PyFilter extends TokenFilter {

    private static final Logger                  logger    = LogManager.getLogger("pyFilter");
    private static final HanyuPinyinOutputFormat format    = new HanyuPinyinOutputFormat();

    private final boolean                        fullPy;
    private final boolean                        firstPyLetter;
    private final boolean                        originalTerm;
    private final String                         caseType;
    private final CharTermAttribute              termAtt   = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute                offsetAtt = addAttribute(OffsetAttribute.class);
    private final OffsetAttribute                ofinput   = input.getAttribute(OffsetAttribute.class);
    private final Queue<String>                  words     = new LinkedList<String>();
    private int                                  start, end;

//    public PyFilter(TokenStream input){
//        this(input,false,true,true,"lower");
//    }

    public PyFilter(TokenStream input, boolean fullPy, boolean firstPyLetter, boolean original, String caseType){
        super(input);
        this.fullPy = fullPy;
        this.firstPyLetter = firstPyLetter;
        this.originalTerm = original;
        this.caseType = caseType;
        switch (this.caseType.toLowerCase()){
            case "lower":{
                format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
                break;
            }
            case "upper":{
                format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
                break;
            }
            case "keep":{
                break;
            }
            default:
                format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        }
    }

    @Override
    public final boolean incrementToken() throws IOException {
        this.clearAttributes();

        if (words.size() <= 0) {
            if (!input.incrementToken()) {
                return false;
            }
            start = ofinput.startOffset();
            end = ofinput.endOffset();
            words.addAll(getPyTongDescartes());
            if (words.size() <= 0) {
                return false;
            }
        }

        String v = words.poll();
        termAtt.setEmpty();
        termAtt.resizeBuffer(v.length());
        termAtt.append(v);
        termAtt.setLength(v.length());
        offsetAtt.setOffset(start, end);
        return true;
    }

    @Override
    public final void end() throws IOException {
        // set final offset
        super.end();
    }

    @Override
    public void reset() throws IOException {
        this.words.clear();
        super.reset();
    }

    static {
        //format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    public static void descartes(List<List<String>> dimvalue, Set<String> result, int layer, String curstring) {
        // 大于一个集合时：
        if (layer < dimvalue.size() - 1) {
            // 大于一个集合时，第一个集合为空
            if (dimvalue.get(layer).size() == 0) descartes(dimvalue, result, layer + 1, curstring);
            else {
                for (int i = 0; i < dimvalue.get(layer).size(); i++) {
                    StringBuilder s1 = new StringBuilder();
                    s1.append(curstring);
                    s1.append(dimvalue.get(layer).get(i));
                    descartes(dimvalue, result, layer + 1, s1.toString());
                }
            }
        }
        // 只有一个集合时：
        else if (layer == dimvalue.size() - 1) {
            // 只有一个集合，且集合中没有元素
            if (dimvalue.get(layer).size() == 0) result.add(curstring);
            // 只有一个集合，且集合中有元素时：其笛卡尔积就是这个集合元素本身
            else {
                for (int i = 0; i < dimvalue.get(layer).size(); i++) {
                    result.add(curstring + dimvalue.get(layer).get(i));
                }
            }
        }
    }

    public Collection<String> getPyTongDescartes() {
        List<List<String>> fullword = new ArrayList<List<String>>();
        List<List<String>> firstword = new ArrayList<List<String>>();

        final char[] buffer = termAtt.buffer();
        final int bufferLength = termAtt.length();
        for (int i = 0; i < bufferLength; i++) {
            String[] strs = null;
            char c = buffer[i];

            if (c > 128) {
                try {
                    strs = PinyinHelper.toHanyuPinyinStringArray(c, format);
                } catch (BadHanyuPinyinOutputFormatCombination badHanyuPinyinOutputFormatCombination) {
                    logger.error("pinyin-filter", badHanyuPinyinOutputFormatCombination);
                }
            }

            if (strs == null) {
                strs = new String[] { Character.toString(c).toLowerCase() };
            }

            List<String> fullt = new ArrayList<String>();
            List<String> firstt = new ArrayList<String>();
            for (String s : strs) {
                if (this.fullPy && s.length() > 0 && !fullt.contains(s)) {
                    fullt.add(s);
                }
                if (this.firstPyLetter && s.length() > 0) {
                    String f = s.substring(0, 1);
                    if (!firstt.contains(f)) firstt.add(f);
                }
            }
            if (fullt.size() > 0) fullword.add(fullt);
            if (firstt.size() > 0) firstword.add(firstt);
        }

        Set<String> ret = new HashSet<String>();
        if (this.originalTerm) {
            ret.add(new String(buffer, 0, bufferLength).toLowerCase());
        }
        if (fullword.size() > 0) {
            descartes(fullword, ret, 0, "");
        }
        if (firstword.size() > 0) {
            descartes(firstword, ret, 0, "");
        }
        return ret;
    }

    public static void main(String[] args) throws IOException {
        String[] vs = { "阿胶", "今天吃饭了吗", "宝马 BMW 2015款 手动HT" };
        for (String v : vs) {
            Analyzer ana = new WhitespaceAnalyzer();
            TokenStream ts = ana.tokenStream("f", new StringReader(v));
            ts = new PyFilter(ts, true, true, true,"lower");
            CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
            ts.reset();

            while (ts.incrementToken()) {
                String charTerm = termAtt.toString();
                System.out.println(charTerm.toString());
            }

            System.out.println("--");
        }
    }
}
