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

package org.apache.roller.weblogger.pojos;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.TemplateRendition.RenditionType;
import org.apache.roller.weblogger.pojos.TemplateRendition.TemplateLanguage;

import java.io.Serializable;
import java.util.Date;


/**
 * Represents a simple static Template.
 *
 * This template is not persisted or managed in any way, this class is here
 * mainly as a wrapper so that we can represent our static template files as
 * an object.
 */
public class StaticThemeTemplate implements ThemeTemplate, Serializable {
    
    private String id;
    private String name;
    private String description;
    private ComponentType action = null;
    private String link;
    private String contents = null;
    private boolean hidden = false;
    private boolean navbar = false;
    private Date lastModified = new Date();
    private TemplateLanguage templateLanguage;
    private String outputContentType = null;
    private RenditionType type = RenditionType.STANDARD;
    
    
    public StaticThemeTemplate(String id, TemplateLanguage lang) {
        this.id = id;
        this.name = id;
        this.description = id;
        this.link = id;
        this.templateLanguage = lang;
    }
    
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public TemplateLanguage getTemplateLanguage() {
        return templateLanguage;
    }

    public void setTemplateLanguage(TemplateLanguage templateLanguage) {
        this.templateLanguage = templateLanguage;
    }
    
    public String getOutputContentType() {
        return outputContentType;
    }

    public RenditionType getType() {
        return type;
    }

    public TemplateRendition getTemplateRendition(RenditionType type) throws WebloggerException {
        return null;
    }

    public void setType(RenditionType type){
          this.type = type;
    }

    public void setOutputContentType(String outputContentType) {
        this.outputContentType = outputContentType;
    }

    public ComponentType getAction() {
        return action;
    }

    public void setAction(ComponentType action) {
        this.action = action;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isNavbar() {
        return navbar;
    }

    public void setNavbar(boolean navbar) {
        this.navbar = navbar;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(String contents) {
        this.contents = contents;
    }

}
