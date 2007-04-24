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

package org.apache.roller.business;

import java.util.List;
import java.util.Map;
import org.apache.roller.pojos.WeblogEntryData;
import org.apache.roller.pojos.WebsiteData;
import org.apache.roller.ui.core.plugins.WeblogEntryEditor;


/**
 * Plugin management for business layer and more generally applied plugins.
 */
public interface PluginManager {
    
    /**
     * Returns true if plugins are present
     */
    public boolean hasPagePlugins();
    
    
    /**
     * Returns a list of all registered weblog entry plugins initialized for
     * use with the specified weblog.
     *
     * @param website        Website being processed
     */
    public Map getWeblogEntryPlugins(WebsiteData website);
    
    
    /**
     * Apply a set of weblog entry plugins to the specified string and
     * return the results.  This method must *NOT* alter the contents of
     * the original entry object.
     *
     * @param entry       Original weblog entry
     * @param plugins     Map of plugins to apply
     * @param str         String to which to apply plugins
     * @return        the transformed text
     */
    public String applyWeblogEntryPlugins(Map pagePlugins, WeblogEntryData entry, String str);
    
    
    /**
     * Release all resources associated with Roller session.
     */
    public void release();
    
}