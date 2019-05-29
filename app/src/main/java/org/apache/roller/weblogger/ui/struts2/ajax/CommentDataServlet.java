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

package org.apache.roller.weblogger.ui.struts2.ajax;

import java.io.IOException;
import javax.json.Json;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.WordUtils;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.pojos.WeblogPermission;
import org.apache.roller.weblogger.ui.core.RollerSession;
import org.apache.roller.weblogger.util.Utilities;


/**
 * Supports GET of comment data in JSON format and PUT of raw comment content.
 */
public class CommentDataServlet extends HttpServlet {

    public void checkAuth(HttpServletRequest request, Weblog weblog) {
    }

    /**
     * Accepts request with comment 'id' parameter and returns comment id and
     * content in JSON format. For example comment with id "3454545346" and
     * content "hi there" will be represented as:
     *    {id : "3454545346", content : "hi there"}
     */
    public void doGet(HttpServletRequest request, 
                      HttpServletResponse response)
            throws ServletException, IOException {
        
        Weblogger roller = WebloggerFactory.getWeblogger();
        try {
            WeblogEntryManager wmgr = roller.getWeblogEntryManager();
            WeblogEntryComment c = wmgr.getComment(request.getParameter("id"));
            if (c == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                // need post permission to view comments
                RollerSession rses = RollerSession.getRollerSession(request);
                Weblog weblog = c.getWeblogEntry().getWebsite();
                if (weblog.hasUserPermission(rses.getAuthenticatedUser(), WeblogPermission.POST)) {
                    String content = sanitizeContent(c.getContent());
                    String json = Json.createObjectBuilder()
                            .add("id", c.getId())
                            .add("content", content)
                            .build()
                            .toString();

                    writeOkResponse(response, json);
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }
            }

        } catch (Exception e) {
            throw new ServletException(e.getMessage());
        }
    }

    /**
     * Accepts request with comment 'id' parameter and replaces specified
     * comment's content with the content in the request.
     */
    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {

        Weblogger roller = WebloggerFactory.getWeblogger();
        try {
            WeblogEntryManager wmgr = roller.getWeblogEntryManager();
            WeblogEntryComment c = wmgr.getComment(request.getParameter("id"));
            if (c == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                // need post permission to edit comments
                RollerSession rses = RollerSession.getRollerSession(request);
                Weblog weblog = c.getWeblogEntry().getWebsite();
                if (weblog.hasUserPermission(rses.getAuthenticatedUser(), WeblogPermission.POST)) {
                    String content = Utilities.streamToString(request.getInputStream());
                    c.setContent(content);
                    // don't update the posttime when updating the comment
                    c.setPostTime(c.getPostTime());
                    wmgr.saveComment(c);
                    roller.flush();

                    c = wmgr.getComment(request.getParameter("id"));
                    content = sanitizeContent(c.getContent());

                    String json = Json.createObjectBuilder()
                            .add("id", c.getId())
                            .add("content", content)
                            .build()
                            .toString();

                    writeOkResponse(response, json);

                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }
            }

        } catch (Exception e) {
            throw new ServletException(e.getMessage());
        }
    }

    private void writeOkResponse(HttpServletResponse response, String content) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html; charset=utf-8");
        response.getWriter().print(content);
        response.flushBuffer();
        response.getWriter().flush();
        response.getWriter().close();
    }

    private String sanitizeContent(String content) {
        return WordUtils.wrap(Utilities.escapeHTML(content), 72);
    }

    public void doPost(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        // not all browsers support PUT
        doPut(request, response);
    }
}
