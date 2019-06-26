package com.bellszhu.elasticsearch.plugin.synonym.tools;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

public class BigramFilter extends TokenFilter {

    private char[]                           buffer;
    private int                              startOffset;
    private int                              endOffset;
    private int                              index     = -1;

    private final CharTermAttribute termAtt   = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posInc    = addAttribute(PositionIncrementAttribute.class);

    public BigramFilter(TokenStream input){
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (index < 0) {
            if (!doNext()) {
                return false;
            }
        }

        if (startOffset + index + 1 >= endOffset) {
            if (!doNext()) {
                return false;
            }
        }

        clearAttributes();
        posInc.setPositionIncrement(1);
        termAtt.copyBuffer(buffer, index, 2);
        offsetAtt.setOffset(startOffset + index, startOffset + index + 2);
        index++;
        return true;
    }

    private boolean doNext() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }
        buffer = termAtt.buffer();
        startOffset = offsetAtt.startOffset();
        endOffset = offsetAtt.endOffset();
        index = 0;
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        index = -1;
    }
}
