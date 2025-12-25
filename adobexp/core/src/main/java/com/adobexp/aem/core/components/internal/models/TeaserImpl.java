/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Copyright 2018 Adobe
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.factory.ModelFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.adobexp.aem.core.components.commons.link.Link;
import com.adobexp.aem.core.components.internal.Heading;
import com.adobexp.aem.core.components.commons.link.LinkManager;
import com.adobexp.aem.core.components.internal.Utils;
import com.adobexp.aem.core.components.models.Image;
import com.adobexp.aem.core.components.models.ListItem;
import com.adobexp.aem.core.components.models.Teaser;
import com.adobexp.aem.core.components.util.ComponentUtils;
import com.day.cq.commons.DownloadResource;
import com.day.cq.commons.ImageResource;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.components.Component;
import com.day.cq.wcm.api.designer.Style;
import com.day.text.Text;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.day.cq.wcm.api.components.ComponentContext;

import static com.adobexp.aem.core.components.util.ComponentUtils.ID_SEPARATOR;

/**
 * Teaser model implementation.
 */
@Model(adaptables = SlingHttpServletRequest.class, adapters = {Teaser.class, ComponentExporter.class}, resourceType = TeaserImpl.RESOURCE_TYPE)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME , extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class TeaserImpl extends AbstractImageDelegatingModel implements Teaser {

    /**
     * The resource type.
     */
    public final static String RESOURCE_TYPE = "adobexp/components/teaser/v1/teaser";

    private static final String IMAGE_ID_PREFIX = "image";

    /**
     * The pre-title text.
     */
    private String pretitle;

    /**
     * The title.
     */
    private String title;

    /**
     * The description.
     */
    private String description;

    /**
     * The title heading level.
     */
    private String titleType;

    /**
     * The target page.
     */
    protected Page targetPage;

    /**
     * The image src.
     */
    private String imageSrc;

    /**
     * Flag indicating if CTA actions are enabled.
     */
    protected boolean actionsEnabled = false;

    /**
     * Flag indicating if the title should be hidden.
     */
    protected boolean titleHidden = false;

    /**
     * Flag indicating if the title type should be hidden.
     */
    private boolean showTitleType = false;

    /**
     * Flag indicating if the description should be hidden.
     */
    protected boolean descriptionHidden = false;

    /**
     * Flag indicating if the image should not be linked.
     */
    private boolean imageLinkHidden = false;

    /**
     * Flag indicating if the pre-title should be hidden.
     */
    private boolean pretitleHidden = false;

    /**
     * Flag indicating if the title should not be linked.
     */
    private boolean titleLinkHidden = false;

    /**
     * Flag indicating if the title should be inherited from the target page.
     */
    protected boolean titleFromPage = false;

    /**
     * Flag indicating if the description should be inherited from the target page.
     */
    protected boolean descriptionFromPage = false;

    /**
     * List of CTA actions.
     */
    private List<Action> actions;

    /**
     * List of properties that should be suppressed on image delegation.
     */
    protected final List<String> hiddenImageResourceProperties = new ArrayList<String>() {{
        add(JcrConstants.JCR_TITLE);
        add(JcrConstants.JCR_DESCRIPTION);
    }};

    protected final Map<String, Object> overriddenImageResourceProperties = new HashMap<>();

    /**
     * The teaser link
     */
    @SuppressWarnings("rawtypes")
    protected Link link;

    @ValueMapValue(injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    protected String linkTarget;

    /**
     * The current component.
     */
    @ScriptVariable
    private Component component;

    /**
     * The page manager.
     */
    @ScriptVariable
    protected PageManager pageManager;

    /**
     * The current style.
     */
    @ScriptVariable
    protected Style currentStyle;

    /**
     * The model factory service.
     */
    @OSGiService
    private ModelFactory modelFactory;

    @Self
    protected LinkManager linkManager;

    @SlingObject
    protected Resource resource;

    @Self
    protected SlingHttpServletRequest request;

    /**
     * Initialize the model.
     */
    @PostConstruct
    protected void initModel() {
        titleFromPage = true;
        descriptionFromPage = true;
        actionsEnabled = true;
        initProperties();
        initImage();
        initLink();
    }

    private String id;

    @ScriptVariable(injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    private Page currentPage;

    @ScriptVariable(injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    protected ComponentContext componentContext;

    /**
     * Initialize the properties.
     */
    protected void initProperties() {
        ValueMap properties = resource.getValueMap();

        pretitleHidden = currentStyle.get(Teaser.PN_PRETITLE_HIDDEN, pretitleHidden);
        titleHidden = currentStyle.get(Teaser.PN_TITLE_HIDDEN, titleHidden);
        descriptionHidden = currentStyle.get(Teaser.PN_DESCRIPTION_HIDDEN, descriptionHidden);
        titleType = currentStyle.get(Teaser.PN_TITLE_TYPE, titleType);
        showTitleType = currentStyle.get(Teaser.PN_SHOW_TITLE_TYPE, showTitleType);
        imageLinkHidden = currentStyle.get(Teaser.PN_IMAGE_LINK_HIDDEN, imageLinkHidden);
        titleLinkHidden = currentStyle.get(Teaser.PN_TITLE_LINK_HIDDEN, titleLinkHidden);
        if (imageLinkHidden) {
            hiddenImageResourceProperties.add(Link.PN_LINK_URL);
        }
        actionsEnabled = !currentStyle.get(Teaser.PN_ACTIONS_DISABLED, !properties.get(Teaser.PN_ACTIONS_ENABLED, actionsEnabled));

        titleFromPage = properties.get(Teaser.PN_TITLE_FROM_PAGE, titleFromPage);
        descriptionFromPage = properties.get(Teaser.PN_DESCRIPTION_FROM_PAGE, descriptionFromPage);
    }

    /**
     * Initialize the image.
     */
    protected void initImage() {

        overriddenImageResourceProperties.put(com.day.cq.wcm.foundation.Image.PN_LINK_URL, getTargetPage().map(Page::getPath).orElse(null));
        overriddenImageResourceProperties.put(Teaser.PN_ACTIONS_ENABLED, Boolean.valueOf(actionsEnabled).toString());
        overriddenImageResourceProperties.put("id", String.join(ID_SEPARATOR, this.getId(), IMAGE_ID_PREFIX));

        // Only allow image to render links if all other elements are empty
        if (!(StringUtils.isAllBlank(getTitle(), getPretitle(), getDescription()) && getTeaserActions().isEmpty())) {
            overriddenImageResourceProperties.put(Teaser.PN_IMAGE_LINK_HIDDEN, Boolean.TRUE);
        }

        if (this.hasImage()) {
            this.setImageResource(component, request.getResource(), hiddenImageResourceProperties, overriddenImageResourceProperties);
        }
    }

    /**
     * Initialize the link.
     */
    protected void initLink() {
        link = linkManager.get(resource).withLinkUrlPropertyName(Link.PN_LINK_URL).build();
    }

    protected Action newAction(Resource actionRes, Component component) {
        return new Action(actionRes, getId(), component);
    }

    public String getId() {
        if (id == null) {
            this.id = ComponentUtils.getId(this.resource, this.currentPage, null, this.componentContext);
        }
        return id;
    }

    @Override
    public boolean isActionsEnabled() {
        return actionsEnabled;
    }

    /**
     * Get the list of teaser actions.
     *
     * @return List of teaser actions.
     */
    @NotNull
    protected List<Action> getTeaserActions() {
        if (this.actions == null) {
            this.actions = Optional.ofNullable(this.isActionsEnabled() ? this.resource.getChild(Teaser.NN_ACTIONS) : null)
                .map(Resource::getChildren)
                .map(Iterable::spliterator)
                .map(s -> StreamSupport.stream(s, false))
                .orElseGet(Stream::empty)
                .map(action -> newAction(action, component))
                .collect(Collectors.toList());
        }
        return this.actions;
    }

    @Override
    public List<ListItem> getActions() {
        return Collections.unmodifiableList(this.getTeaserActions());
    }

    @Override
    public String getLinkURL() {
        return (link != null) ? link.getURL() : null;
    }

    /**
     * Get the image path.
     *
     * Note: This method exists only for JSON model.
     *
     * @return The image src path if it exists, null if it does not.
     */
    @JsonProperty(value = "imagePath")
    @Nullable
    public String getImagePath() {
        if (imageSrc == null) {
            this.imageSrc = Optional.ofNullable(this.getImageResource())
                .map(imageResource -> this.modelFactory.getModelFromWrappedRequest(this.request, imageResource, Image.class))
                .map(Image::getSrc)
                .orElse(null);
        }
        return this.imageSrc;
    }

    @Override
    public boolean isImageLinkHidden() {
        return imageLinkHidden;
    }

    @Override
    public String getPretitle() {
        if (this.pretitle == null && !pretitleHidden) {
            this.pretitle = this.resource.getValueMap().get("pretitle", String.class);
        }
        return pretitle;
    }

    @Override
    public boolean isTitleLinkHidden() {
        return titleLinkHidden;
    }

    @Override
    public String getTitleType() {
        if (showTitleType) {
            titleType = resource.getValueMap().get(Teaser.PN_TITLE_TYPE, titleType);
        }

        Heading heading = Heading.getHeading(titleType);
        if (heading != null) {
            return heading.getElement();
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    @Nullable
    public Link getLink() {
        return link.isValid() ? link : null;
    }

    @NotNull
    protected Optional<Page> getTargetPage() {
        if (this.targetPage == null) {
            String linkURL = resource.getValueMap().get(ImageResource.PN_LINK_URL, String.class);
            if (StringUtils.isNotEmpty(linkURL)) {
                this.targetPage = Optional.ofNullable(this.resource.getValueMap().get(ImageResource.PN_LINK_URL, String.class))
                        .map(this.pageManager::getPage).orElse(null);
            } else if (actionsEnabled && getActions().size() > 0) {
                this.targetPage = getTeaserActions().stream().findFirst()
                        .flatMap(com.adobexp.aem.core.components.internal.models.TeaserImpl.Action::getCtaPage)
                        .orElse(null);
            } else {
                targetPage = currentPage;
            }
        }
        return Optional.ofNullable(this.targetPage);
    }

    @Override
    public String getTitle() {
        if (this.title == null && !this.titleHidden) {
            if (titleFromPage) {
                this.title = this.getTargetPage()
                        .map(tp -> StringUtils.defaultIfEmpty(tp.getPageTitle(), tp.getTitle()))
                        .orElseGet(() -> this.getTeaserActions().stream().findFirst()
                                .map(com.adobexp.aem.core.components.internal.models.TeaserImpl.Action::getTitle)
                                .orElseGet(() -> Optional.ofNullable(this.currentPage)
                                        .map(cp -> StringUtils.defaultIfEmpty(cp.getPageTitle(), cp.getTitle()))
                                        .orElse(null)));
            } else {
                this.title = this.resource.getValueMap().get(JcrConstants.JCR_TITLE, String.class);
            }
        }
        return title;
    }

    @Override
    public String getDescription() {
        if (this.description == null && !this.descriptionHidden) {
            if (descriptionFromPage) {
                this.description = this.getTargetPage().map(Optional::of).orElseGet(() -> Optional.ofNullable(this.currentPage))
                        .map(Page::getDescription)
                        // page properties uses a plain text field - which may contain special chars that need to be escaped in HTML
                        // because the resulting description from the teaser is expected to be HTML produced by the RTE editor
                        .map(Text::escapeXml)
                        .orElse(null);
            } else {
                this.description = this.resource.getValueMap().get(JcrConstants.JCR_DESCRIPTION, String.class);
            }
        }
        return this.description;
    }

    protected boolean hasImage() {
        // As Teaser v2 supports inheritance from the featured image of the page, the current resource is wrapped and
        // augmented with the inherited properties and child resources of the featured image.
        Resource wrappedResource = Utils.getWrappedImageResourceWithInheritance(resource, linkManager, currentStyle, currentPage);
        return Optional.ofNullable(wrappedResource.getValueMap().get(DownloadResource.PN_REFERENCE, String.class))
                .map(request.getResourceResolver()::getResource)
                .orElseGet(() -> wrappedResource.getChild(DownloadResource.NN_FILE)) != null ||
                Optional.ofNullable(wrappedResource.getValueMap().get(DownloadResource.PN_REFERENCE, String.class)).filter(ImageImpl::isNgdmImageReference).isPresent();
    }


    /**
     * Teaser CTA.
     */
    @SuppressWarnings("unused")
    @JsonIgnoreProperties({"path", "description", "lastModified", "name", "ctaPage"})
    public class Action extends AbstractListItemImpl implements ListItem {

        /**
         * ID prefix.
         */
        private static final String CTA_ID_PREFIX = "cta";


        /**
         * The resource for this CTA.
         */
        @NotNull
        private final Resource ctaResource;


        protected final String ctaTitle;
        /**
         * The CTA link.
         */
        @SuppressWarnings("rawtypes")
        protected final Link ctaLink;

        /**
         * The ID of the teaser that contains this action.
         */
        private final String ctaParentId;

        /**
         * The ID of this action.
         */
        private String ctaId;

        /**
         * Create a CTA.
         *
         * @param actionRes The action resource.
         * @param parentId The ID of the containing Teaser.
         */
        public Action(@NotNull final Resource actionRes, final String parentId, Component component) {
            super(parentId, actionRes, component);
            ctaParentId = parentId;
            ctaResource = actionRes;
            ValueMap ctaProperties = actionRes.getValueMap();
            ctaTitle = ctaProperties.get(PN_ACTION_TEXT, String.class);
            ctaLink = linkManager.get(actionRes).withLinkUrlPropertyName(PN_ACTION_LINK).build();
            if (component != null) {
                this.dataLayerType = component.getResourceType() + "/" + CTA_ID_PREFIX;
            }
        }

        @SuppressWarnings("rawtypes")
        @Override
        @JsonIgnore
        @Nullable
        public Link getLink() {
            return ctaLink;
        }

        /**
         * Get the referenced page.
         *
         * @return The referenced page if this CTA references a page, empty if not.
         */
        @NotNull
        public Optional<Page> getCtaPage() {
            return Optional.ofNullable(ctaLink.getReference())
                    .filter(reference -> reference instanceof Page)
                    .map(reference -> (Page) reference);
        }

        @Nullable
        @Override
        public String getTitle() {
            return ctaTitle;
        }

        @Nullable
        @Override
        public String getPath() {
            return getCtaPage()
                    .map(Page::getPath)
                    // probably would make more sense to return null when not page is target, but we keep this for backward compatibility
                    .orElse(ctaLink.getURL());
        }

        @Nullable
        @Override
        public String getURL() {
            return ctaLink.getURL();
        }

        @Override
        public String getId() {
            if (ctaId == null) {
                ctaId = Optional.ofNullable(ctaResource.getValueMap().get("id", String.class))
                    .filter(StringUtils::isNotEmpty)
                    .map(id -> StringUtils.replace(StringUtils.normalizeSpace(StringUtils.trim(id)), " ", ID_SEPARATOR))
                    .orElseGet(() ->
                        ComponentUtils.generateId(StringUtils.join(ctaParentId, ID_SEPARATOR, CTA_ID_PREFIX), this.ctaResource.getPath())
                    );
            }
            return ctaId;
        }
    }

}
