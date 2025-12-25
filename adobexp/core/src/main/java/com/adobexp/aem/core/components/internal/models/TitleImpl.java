/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Copyright 2017 Adobe
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package com.adobexp.aem.core.components.internal.models;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.jetbrains.annotations.Nullable;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.adobexp.aem.core.components.commons.link.Link;
import com.adobexp.aem.core.components.internal.Heading;
import com.adobexp.aem.core.components.commons.link.LinkManager;
import com.adobexp.aem.core.components.models.Title;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.designer.Style;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Model(adaptables = SlingHttpServletRequest.class,
       adapters = {Title.class, ComponentExporter.class},
       resourceType = {TitleImpl.RESOURCE_TYPE_V1})
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME, extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class TitleImpl implements Title {

    protected static final String RESOURCE_TYPE_V1 = "adobexp/components/title/v1/title";

    private boolean linkDisabled = false;

    @Self
    private SlingHttpServletRequest request;

    @ScriptVariable
    private Resource resource;

    @ScriptVariable
    private PageManager pageManager;

    @ScriptVariable
    private Page currentPage;

    @ScriptVariable(injectionStrategy = InjectionStrategy.OPTIONAL)
    @JsonIgnore
    @Nullable
    private Style currentStyle;

    @ValueMapValue(injectionStrategy = InjectionStrategy.OPTIONAL, name = JcrConstants.JCR_TITLE)
    @Nullable
    private String title;

    @ValueMapValue(injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    private String type;

    @Self
    private LinkManager linkManager;
    @SuppressWarnings("rawtypes")
    protected Link link;

    /**
     * The {@link com.adobe.cq.wcm.core.components.internal.Heading} object for the type of this title.
     */
    private Heading heading;

    @PostConstruct
    private void initModel() {
        if (StringUtils.isBlank(title)) {
            title = StringUtils.defaultIfEmpty(currentPage.getPageTitle(), currentPage.getTitle());
        }

        if (heading == null) {
            heading = Heading.getHeading(type);
            if (heading == null && currentStyle != null) {
                heading = Heading.getHeading(currentStyle.get(PN_DESIGN_DEFAULT_TYPE, String.class));
            }
        }

        link = linkManager.get(resource).build();

        if(currentStyle != null) {
            linkDisabled = currentStyle.get(Title.PN_TITLE_LINK_DISABLED, linkDisabled);
        }
    }

    @Override
    public String getText() {
        return title;
    }

    @Override
    public String getType() {
        if (heading != null) {
            return heading.getElement();
        }
        return null;
    }

    @Override
    @Deprecated
    public String getLinkURL() {
        return link.getURL();
    }


    @SuppressWarnings("rawtypes")
    @Override
    public Link getLink() {
        return link.isValid() ? link : null;
    }

    @Override
    public boolean isLinkDisabled() {
        return linkDisabled;
    }
}
