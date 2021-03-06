/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.business.plugins.comment;

import java.io.BufferedReader;
import java.io.StringReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.plugins.commons.function.PlainTextToHTML;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;


/**
 * Comment plugin which turns plain text paragraph formatting into html
 * paragraph formatting using <p> and <br/> tags.
 */
public class AutoformatPlugin implements WeblogEntryCommentPlugin {
    
    private static final Log LOG = LogFactory.getLog(AutoformatPlugin.class);
    
    
    public AutoformatPlugin() {
        // no-op
    }
    
    
    /**
     * Unique identifier.  This should never change. 
     */
    public String getId() {
        return "AutoFormat";
    }
    
    
    public String getName() {
        return "Auto Format";
    }
    
    
    public String getDescription() {
        return "Converts plain text style paragraphs into html paragraphs.";
    }
    
    
    public String render(final WeblogEntryComment comment, String text) {
        
        LOG.debug("starting value:\n" + text);
        
        /* 
         * setup a buffered reader and iterate through each line
         * inserting html as needed
         *
         * NOTE: we consider a paragraph to be 2 endlines with no text between them
         */
        try {
            return new PlainTextToHTML().apply(text);

        } catch(Exception e) {
            LOG.warn("trouble rendering text.", e);
            return "";
        }
    }
    
}
