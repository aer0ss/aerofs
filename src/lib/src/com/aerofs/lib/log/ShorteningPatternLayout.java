/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.log;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;

class ShorteningPatternLayout extends PatternLayout
{
    public ShorteningPatternLayout(String pattern)
    {
        super(pattern);
    }

    @Override
    protected PatternParser createPatternParser(String pattern)
    {
        return new PatternParser(pattern)
        {
            @Override
            protected void finalizeConverter(char c)
            {
                PatternConverter pc;
                switch (c) {
                case 'c':
                    pc = new CategoryPatternConverter(formattingInfo, extractPrecisionOption());
                    currentLiteral.setLength(0);
                    break;

                default:
                    super.finalizeConverter(c);
                    return;
                }

                addConverter(pc);
            }
        };
    }
}
