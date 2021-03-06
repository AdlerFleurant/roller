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

package org.apache.roller.weblogger.business.startup;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.roller.weblogger.WebloggerException;


/**
 * Exception generated from Weblogger startup process.
 */
public class StartupException extends WebloggerException {
    
    private final List<String> startupLog;
    
    
    public StartupException(String msg) {
        super(msg);
        this.startupLog = Collections.emptyList();
    }
    
    public StartupException(String msg, Throwable t) {
        super(msg, t);
        this.startupLog = Collections.emptyList();
    }
    
    public StartupException(String msg, List<String> log) {
        super(msg);

        this.startupLog = Objects.requireNonNullElse(log, Collections.emptyList());
    }
    
    public StartupException(String msg, Throwable t, List<String> log) {
        super(msg, t);

        this.startupLog = Objects.requireNonNullElse(log, Collections.emptyList());
    }
    
    
    public List<String> getStartupLog() {
        return startupLog;
    }
    
}
