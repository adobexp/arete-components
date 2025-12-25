/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Copyright 2019 Adobe
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

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.factory.ModelFactory;
import org.jetbrains.annotations.Nullable;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ContainerExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.adobexp.aem.core.components.models.ExperienceFragment;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Experience Fragment model implementation.
 */
@Model(adaptables = SlingHttpServletRequest.class,
    adapters = {ExperienceFragment.class, ComponentExporter.class, ContainerExporter.class },
    resourceType = { ExperienceFragmentImpl.RESOURCE_TYPE_V2 })
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME, extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class ExperienceFragmentImpl implements ExperienceFragment {

    /**
     * The experience fragment component resource type.
     */
    public static final String RESOURCE_TYPE_V2 = "adobexp/components/experiencefragment/v2/experiencefragment";

    /**
     * The current request.
     */
    @Self
    private SlingHttpServletRequest request;

    @Self
    private ExperienceFragmentDataImpl data;

    /**
     * The model factory service.
     */
    @OSGiService
    private ModelFactory modelFactory;

    /**
     * Name of the experience fragment.
     */
    private String name;


    @Override
    @Nullable
    public String getLocalizedFragmentVariationPath() {
        return data.getLocalizedFragmentVariationPath();
    }

    @Override
    @JsonIgnore
    @Nullable
    public String getName() {
        if (this.name == null) {
            this.name = Optional.ofNullable(this.request.getResourceResolver().adaptTo(PageManager.class))
                .flatMap(pm -> Optional.ofNullable(this.getLocalizedFragmentVariationPath())
                    .map(pm::getContainingPage))
                .map(Page::getParent)
                .map(Page::getName)
                .orElse(null);
        }
        return this.name;
    }

    @Override
    @JsonInclude
    public boolean isConfigured() {
        return StringUtils.isNotEmpty(this.getLocalizedFragmentVariationPath());
    }
}
