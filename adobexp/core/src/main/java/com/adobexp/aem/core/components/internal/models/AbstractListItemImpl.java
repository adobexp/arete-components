/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Copyright 2020 Adobe
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

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import com.adobexp.aem.core.components.models.ListItem;
import com.adobexp.aem.core.components.util.ComponentUtils;
import com.day.cq.wcm.api.components.Component;

/**
 * Abstract helper class for ListItem implementations.
 * Generates an ID for the item, using the ID of its parent as a prefix
 */
public abstract class AbstractListItemImpl implements ListItem {

    /**
     * Prefix prepended to the item ID.
     */
    private static final String ITEM_ID_PREFIX = "item";

    /**
     * The ID of the component that contains this list item.
     */
    protected String parentId;

    /**
     * The path of this list item.
     */
    protected String path;

    /**
     * Data layer type.
     */
    protected String dataLayerType;

    /**
     * The resource of the list item.
     */
    protected Resource resource;

    /**
     * Construct a list item.
     *
     * @param parentId The ID of the containing component.
     * @param resource The resource of the list item.
     * @param component The component that contains this list item.
     */
    protected AbstractListItemImpl(String parentId, Resource resource, Component component) {
        this.parentId = parentId;
        if (resource != null) {
            this.path = resource.getPath();
        }
        if (component != null) {
            this.dataLayerType = component.getResourceType() + "/" + ITEM_ID_PREFIX;
        }
        this.resource = resource;
    }

    @NotNull
    public String getId() {
        return ComponentUtils.generateId(StringUtils.join(parentId, ComponentUtils.ID_SEPARATOR, ITEM_ID_PREFIX), path);
    }
}
