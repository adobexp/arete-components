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

import javax.annotation.PostConstruct;

import com.day.cq.wcm.api.designer.Style;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.adobexp.aem.core.components.models.LayoutContainer;

/**
 * Layout container model implementation.
 */
@Model(adaptables = SlingHttpServletRequest.class, adapters = LayoutContainer.class, resourceType = LayoutContainerImpl.RESOURCE_TYPE_V1)
public class LayoutContainerImpl implements LayoutContainer {

    /**
     * The resource type.
     */
    protected static final String RESOURCE_TYPE_V1 = "adobexp/components/container/v1/container";
    protected static final String GHOST_COMPONENT_RESOURCE_TYPE = "wcm/msm/components/ghost";

    /**
     * The current resource.
     */
    @SlingObject
    protected Resource resource;
    
    /**
     * The layout type.
     */
    private LayoutType layout;

    /**
     * The accessibility label.
     */
    @ValueMapValue(injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    private String accessibilityLabel;

    /**
     * The role attribute.
     */
    @ValueMapValue(injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    private String roleAttribute;

    /**
     * The current style for this component.
     */
    @ScriptVariable(injectionStrategy = InjectionStrategy.OPTIONAL)
    @JsonIgnore
    @Nullable
    protected Style currentStyle;

    /**
     * Initialize the model.
     */
    @PostConstruct
    protected void initModel() {
        // Note: this can be simplified using Optional.or() in JDK 11
        this.layout = Optional.ofNullable(
            Optional.ofNullable(resource.getValueMap().get(LayoutContainer.PN_LAYOUT, String.class))
                .orElseGet(() -> Optional.ofNullable(currentStyle)
                    .map(style -> currentStyle.get(LayoutContainer.PN_LAYOUT, String.class))
                    .orElse(null)
                ))
            .map(LayoutType::getLayoutType)
            .orElse(LayoutType.SIMPLE);
    }



    @Override
    public @NotNull LayoutType getLayout() {
        return layout;
    }

    @Override
    @Nullable
    public String getAccessibilityLabel() {
        return accessibilityLabel;
    }

    @Override
    @Nullable
    public String getRoleAttribute() {
        return roleAttribute;
    }
}
