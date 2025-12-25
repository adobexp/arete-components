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

import static com.day.cq.commons.jcr.JcrConstants.JCR_CONTENT;
import static com.day.cq.commons.jcr.JcrConstants.JCR_MIMETYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.PostConstruct;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.osgi.services.HttpClientBuilderFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.adobexp.aem.core.components.commons.link.Link;
import com.adobexp.aem.core.components.commons.link.LinkManager;
import com.adobexp.aem.core.components.internal.helper.image.AssetDeliveryHelper;
import com.adobexp.aem.core.components.internal.link.LinkUtil;
import com.adobexp.aem.core.components.internal.servlets.AdaptiveImageServlet;
import com.adobexp.aem.core.components.internal.servlets.EnhancedRendition;
import com.adobexp.aem.core.components.models.Image;
import com.adobexp.aem.core.components.models.ImageArea;
import com.adobe.cq.wcm.spi.AssetDelivery;
import com.day.cq.commons.DownloadResource;
import com.day.cq.commons.ImageResource;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.scene7.api.constants.Scene7AssetType;
import com.day.cq.dam.scene7.api.constants.Scene7Constants;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Template;
import com.day.cq.wcm.api.designer.Style;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.adobe.cq.ui.wcm.commons.config.NextGenDynamicMediaConfig;
import java.util.Map;
import java.util.Scanner;
import java.awt.Dimension;
import java.io.IOException;
import java.io.StringReader;

import static com.adobexp.aem.core.components.models.Teaser.PN_IMAGE_LINK_HIDDEN;
import static com.adobexp.aem.core.components.internal.Utils.getWrappedImageResourceWithInheritance;

@Model(adaptables = SlingHttpServletRequest.class, adapters = {Image.class, ComponentExporter.class}, resourceType = ImageImpl.RESOURCE_TYPE)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME, extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class ImageImpl implements Image {

    public static final String RESOURCE_TYPE = "adobexp/components/image/v1/image";
    private static final String DEFAULT_EXTENSION = "jpeg";

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageImpl.class);
    protected static final String DOT = ".";
    protected static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";
    protected static final String MIME_TYPE_IMAGE_SVG = "image/svg+xml";
    private static final String MIME_TYPE_IMAGE_PREFIX = "image/";
    protected static final String SEO_NAME_FILTER_PATTERN = "[\\W|_]";

    @SlingObject
    protected Resource resource;

    @ScriptVariable
    protected PageManager pageManager;

    @ScriptVariable
    protected Page currentPage;

    @ScriptVariable
    protected Style currentStyle;

    @OSGiService
    protected MimeTypeService mimeTypeService;

    @OSGiService(injectionStrategy = InjectionStrategy.OPTIONAL)
    protected AssetDelivery assetDelivery;

    @Self
    protected LinkManager linkManager;

    @Self
    private SlingHttpServletRequest request;


    @ValueMapValue(name = "imageModifiers", injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    protected String imageModifiers;

    @ValueMapValue(name = "imagePreset", injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    protected String imagePreset;

    @ValueMapValue(name = "smartCropRendition", injectionStrategy = InjectionStrategy.OPTIONAL)
    @Nullable
    protected String smartCropRendition;

    static final String SRC_URI_TEMPLATE_WIDTH_VAR = "{.width}";

    static final String SRC_URI_TEMPLATE_WIDTH_VAR_ASSET_DELIVERY = "{width}";

    protected static final String SMART_CROP_AUTO = "SmartCrop:Auto";

    private static final String CONTENT_POLICY_DELEGATE_PATH = "contentPolicyDelegatePath";

    private static final String DM_IMAGE_SERVER_PATH = "/is/image/";

    private static final String DM_CONTENT_SERVER_PATH = "/is/content/";

    protected String srcUriTemplate;

    protected List<ImageArea> areas;

    protected int lazyThreshold;

    protected boolean dmImage = false;

    protected String uuid;

    protected ValueMap properties;
    protected String fileReference;
    protected String alt;
    protected String title;
    protected String externalImageResourcePath;

    protected String src;
    protected String[] smartImages = new String[]{};
    protected int[] smartSizes = new int[0];
    protected String json;
    protected boolean displayPopupTitle;
    protected boolean isDecorative;

    protected boolean hasContent;
    protected String mimeType;
    protected String selector;
    protected String extension;
    protected long lastModifiedDate = 0;
    protected boolean inTemplate = false;
    protected boolean hasExternalImageResource = false;
    protected String baseResourcePath;
    protected String templateRelativePath;
    protected boolean disableLazyLoading;
    protected int jpegQuality;
    protected String imageName;
    protected Resource fileResource;
    @SuppressWarnings("rawtypes")
    protected Link link;
    protected boolean useAssetDelivery = false;





    private static final String URI_WIDTH_PLACEHOLDER_ENCODED = "%7B.width%7D";
    private static final String URI_WIDTH_PLACEHOLDER = "{.width}";
    private static final String EMPTY_PIXEL = "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==";
    static final int DEFAULT_NGDM_ASSET_WIDTH = 640;

    @OSGiService
    @Optional
    private NextGenDynamicMediaConfig nextGenDynamicMediaConfig;

    @OSGiService
    @Optional
    private HttpClientBuilderFactory clientBuilderFactory;

    private boolean imageLinkHidden = false;

    private String srcSet = StringUtils.EMPTY;
    private String sizes;

    private Dimension dimension;

    private boolean ngdmImage = false;
    private CloseableHttpClient client;
    private static final String PATH_PLACEHOLDER_ASSET_ID = "{asset-id}";
    private String metadataDeliveryEndpoint;

    public ImageImpl() {
        selector = AdaptiveImageServlet.CORE_DEFAULT_SELECTOR;
    }

    /**
     * needs to be protected so that implementations that extend this one can optionally call super.initModel; Sling Models doesn't
     * correctly handle this scenario, although the documentation says something else: see
     * https://github.com/apache/sling-org-apache-sling-models-impl/commit/45570dab4818dc9f626f89f8aa6dbca6557dcc42#diff-8b70000e82308890fe104a598cd2bec2R731
     */
    @PostConstruct
    protected void initModel() {
        if (isNgdmSupportAvailable()) {
            initNextGenerationDynamicMedia();
        }
        initResource();
        initWrappedResourceFields();

        Asset asset = resolveAssetOrInlineBinary();

        if (hasContent && !initRasterizedImageModel(asset)) {
            return;
        }

        applyDamMetadataAndUriTemplate(asset);

        this.lazyThreshold = currentStyle.get(PN_DESIGN_LAZY_THRESHOLD, 0);
    }

    private void initWrappedResourceFields() {
        // Note: all the properties and child resources of the image should be retrieved through the wrapped 'resource' object
        // and not through the injected properties of the model.
        properties = resource.getValueMap();
        fileResource = resource.getChild(DownloadResource.NN_FILE);
        fileReference = properties.get(DownloadResource.PN_REFERENCE, String.class);
        alt = properties.get(ImageResource.PN_ALT, String.class);
        title = properties.get(JcrConstants.JCR_TITLE, String.class);
        externalImageResourcePath = properties.get(PN_EXTERNAL_IMAGE_RESOURCE_PATH, String.class);
        if (StringUtils.isNotEmpty(externalImageResourcePath)) {
            hasExternalImageResource = true;
        }

        mimeType = MIME_TYPE_IMAGE_JPEG;
        displayPopupTitle = properties.get(PN_DISPLAY_POPUP_TITLE, currentStyle.get(PN_DISPLAY_POPUP_TITLE, false));
        isDecorative = properties.get(PN_IS_DECORATIVE, currentStyle.get(PN_IS_DECORATIVE, false));
        useAssetDelivery = currentStyle.get(PN_DESIGN_ASSET_DELIVERY_ENABLED, false) && assetDelivery != null;
    }

    @Nullable
    private Asset resolveAssetOrInlineBinary() {
        Asset asset = null;

        if (StringUtils.isNotEmpty(fileReference)) {
            // the image is coming from DAM
            final Resource assetResource = request.getResourceResolver().getResource(fileReference);
            if (assetResource != null) {
                asset = assetResource.adaptTo(Asset.class);
                if (asset != null) {
                    mimeType = PropertiesUtil.toString(asset.getMimeType(), MIME_TYPE_IMAGE_JPEG);
                    imageName = getImageNameFromAsset(asset);
                    hasContent = true;
                } else {
                    useAssetDelivery = false;
                    LOGGER.error("Unable to adapt resource '{}' used by image '{}' to an asset.", fileReference, resource.getPath());
                }
            } else {
                useAssetDelivery = false;
                // handle the case where the image is not coming from DAM but from a different source (e.g. NGDM)
                if (!hasContent) {
                    LOGGER.error("Unable to find resource '{}' used by image '{}'.", fileReference, resource.getPath());
                }
            }
        } else {
            useAssetDelivery = false;
            if (fileResource != null) {
                mimeType = PropertiesUtil.toString(fileResource.getResourceMetadata().get(ResourceMetadata.CONTENT_TYPE), null);
                if (StringUtils.isEmpty(mimeType)) {
                    Resource fileResourceContent = fileResource.getChild(JCR_CONTENT);
                    if (fileResourceContent != null) {
                        ValueMap fileProperties = fileResourceContent.getValueMap();
                        mimeType = fileProperties.get(JCR_MIMETYPE, MIME_TYPE_IMAGE_JPEG);
                    }
                }
                String fileName = properties.get(ImageResource.PN_FILE_NAME, String.class);
                imageName = StringUtils.isNotEmpty(fileName) ? getSeoFriendlyName(FilenameUtils.getBaseName(fileName)) : "";
                hasContent = true;
            }
        }

        return asset;
    }

    /**
     * @return {@code true} if initialization should continue; {@code false} if the model should return early
     */
    private boolean initRasterizedImageModel(@Nullable Asset asset) {
        if (hasContent) {
            // validate if correct mime type (i.e. rasterized image)
            if (!mimeType.startsWith(MIME_TYPE_IMAGE_PREFIX)) {
                LOGGER.error("Image at {} uses a binary with a non-image mime type ({})", resource.getPath(), mimeType);
                hasContent = false;
                return false;
            }
            // The jcr:mimeType property may contain a charset suffix (image/jpeg;charset=UTF-8).
            // For example if a file was written with JcrUtils#putFile and an optional charset was provided.
            // Check for the suffix and remove as necessary.
            mimeType = mimeType.split(";")[0];
            extension = mimeTypeService.getExtension(mimeType);
            Calendar lastModified = properties.get(JcrConstants.JCR_LASTMODIFIED, Calendar.class);
            if (lastModified == null) {
                lastModified = properties.get(NameConstants.PN_PAGE_LAST_MOD, Calendar.class);
            }
            if (lastModified != null) {
                lastModifiedDate = lastModified.getTimeInMillis();
            }
            if (asset != null) {
                long assetLastModifiedDate = asset.getLastModified();
                if (assetLastModifiedDate > lastModifiedDate) {
                    lastModifiedDate = assetLastModifiedDate;
                }
            }
            if (extension == null || extension.equalsIgnoreCase("tif") || extension.equalsIgnoreCase("tiff")) {
                extension = DEFAULT_EXTENSION;
            }
            disableLazyLoading = currentStyle.get(PN_DESIGN_LAZY_LOADING_ENABLED, false);
            jpegQuality = currentStyle.get(PN_DESIGN_JPEG_QUALITY, AdaptiveImageServlet.DEFAULT_JPEG_QUALITY);
            int index = 0;
            Template template = currentPage.getTemplate();
            if (template != null && resource.getPath().startsWith(template.getPath())) {
                inTemplate = true;
                baseResourcePath = currentPage.getPath();
                templateRelativePath = resource.getPath().substring(template.getPath().length());
            } else {
                baseResourcePath = resource.getPath();
                if (resource.getResourceResolver().getResource(resource.getPath()) == null) {
                    // synthetic merged resource, use the current page path as base path
                    baseResourcePath = currentPage.getPath();
                }
            }
            baseResourcePath = resource.getResourceResolver().map(request, baseResourcePath);
            if (smartSizesSupported()) {
                Set<Integer> supportedRenditionWidths = getSupportedRenditionWidths();
                smartImages = new String[supportedRenditionWidths.size()];
                smartSizes = new int[supportedRenditionWidths.size()];
                for (Integer width : supportedRenditionWidths) {
                    String smartImage = "";
                    if (useAssetDelivery) {
                        smartImage = AssetDeliveryHelper.getSrc(assetDelivery, resource, imageName, extension, width, jpegQuality);
                    }
                    if (StringUtils.isEmpty(smartImage)) {
                        smartImage = baseResourcePath + DOT +
                            selector + DOT + jpegQuality + DOT + width + DOT + extension +
                            (inTemplate ? Text.escapePath(templateRelativePath) : hasExternalImageResource ? externalImageResourcePath : "") +
                            (lastModifiedDate > 0 ? ("/" + lastModifiedDate + (StringUtils.isNotBlank(imageName) ? ("/" + imageName) : "")) : "") +
                            (inTemplate || hasExternalImageResource || lastModifiedDate > 0 ? DOT + extension : "");
                    }
                    smartImages[index] = smartImage;
                    smartSizes[index] = width;
                    index++;
                }
            } else {
                smartImages = new String[0];
                smartSizes = new int[0];
            }

            if (useAssetDelivery) {
                src = AssetDeliveryHelper.getSrc(assetDelivery, resource, imageName, extension,
                    ArrayUtils.isNotEmpty(smartSizes) && smartSizes.length == 1 ? smartSizes[0] : null,
                    jpegQuality);
            }

            if (StringUtils.isEmpty(src)) {
                src = baseResourcePath + DOT + selector + DOT;
                if (smartSizes.length == 1) {
                    src += jpegQuality + DOT + smartSizes[0] + DOT + extension;
                } else {
                    src += extension;
                }
                src += inTemplate ? Text.escapePath(templateRelativePath) : hasExternalImageResource ? externalImageResourcePath : "";
                src += lastModifiedDate > 0 ? "/" + lastModifiedDate + (StringUtils.isNotBlank(imageName) ? "/" + imageName : "") : "";
                src += inTemplate || hasExternalImageResource || lastModifiedDate > 0 ? DOT + extension : "";
            }

            if (!isDecorative) {
                link = linkManager.get(resource).build();
            } else {
                alt = null;
            }
            buildJson();

        }

        return true;
    }

    private void applyDamMetadataAndUriTemplate(@Nullable Asset asset) {
        boolean altValueFromDAM = properties.get(PN_ALT_VALUE_FROM_DAM, currentStyle.get(PN_ALT_VALUE_FROM_DAM, true));
        boolean titleValueFromDAM = properties.get(PN_TITLE_VALUE_FROM_DAM, currentStyle.get(PN_TITLE_VALUE_FROM_DAM, true));
        boolean isDmFeaturesEnabled = currentStyle.get(PN_DESIGN_DYNAMIC_MEDIA_ENABLED, false);
        displayPopupTitle = properties.get(PN_DISPLAY_POPUP_TITLE, currentStyle.get(PN_DISPLAY_POPUP_TITLE, true));
        boolean uuidDisabled = currentStyle.get(PN_UUID_DISABLED, false);
        // if content policy delegate path is provided pass it to the image Uri
        String policyDelegatePath = request.getParameter(CONTENT_POLICY_DELEGATE_PATH);
        String dmImageUrl = null;
        if (StringUtils.isNotEmpty(fileReference)) {
            // the image is coming from DAM
            final Resource assetResource = request.getResourceResolver().getResource(fileReference);
            if (assetResource != null) {
                asset = assetResource.adaptTo(Asset.class);
                if (asset != null) {
                    if (!uuidDisabled) {
                        uuid = asset.getID();
                    } else {
                        uuid = null;
                    }
                    if (!isDecorative && altValueFromDAM) {
                        String damDescription = asset.getMetadataValue(DamConstants.DC_DESCRIPTION);
                        if(StringUtils.isEmpty(damDescription)) {
                            damDescription = asset.getMetadataValue(DamConstants.DC_TITLE);
                        }
                        if (StringUtils.isNotEmpty(damDescription)) {
                            alt = damDescription;
                        }
                    }
                    if (titleValueFromDAM) {
                        title = StringUtils.trimToNull(asset.getMetadataValue(DamConstants.DC_TITLE));
                    }

                    //check "Enable DM features" checkbox
                    //check DM asset - check for "dam:scene7File" metadata value
                    String dmAssetName = asset.getMetadataValue(Scene7Constants.PN_S7_FILE);
                    if(isDmFeaturesEnabled && (!StringUtils.isEmpty(dmAssetName))){
                        dmAssetName = LinkUtil.escapeFragment(dmAssetName);
                        //image is DM
                        dmImage = true;
                        useAssetDelivery = false;
                        //check for publish side
                        boolean isWCMDisabled =  (com.day.cq.wcm.api.WCMMode.fromRequest(request) == com.day.cq.wcm.api.WCMMode.DISABLED);
                        //sets to '/is/image/ or '/is/content' based on dam:scene7Type property
                        String dmServerPath;
                        // '/is/image' DM url is for optimized image delivery supporting run time transformations.
                        //  Use '/is/image' url if dam:scene7Type is explicitly set to 'Image' or DM processor does not set dam:scene7Type
                        if (asset.getMetadataValue(Scene7Constants.PN_S7_TYPE).equals(Scene7AssetType.IMAGE.getValue())
                            || asset.getMetadataValue(Scene7Constants.PN_S7_TYPE).equals(StringUtils.EMPTY)) {
                            dmServerPath = DM_IMAGE_SERVER_PATH;
                        } else {
                        // All other file types should be loaded as content via '/is/content'
                            dmServerPath = DM_CONTENT_SERVER_PATH;
                        }
                        String dmServerUrl;
                        // for Author
                        if (!isWCMDisabled) {
                            dmServerUrl = dmServerPath;
                        } else {
                            // for Publish
                            dmServerUrl = asset.getMetadataValue(Scene7Constants.PN_S7_DOMAIN) + dmServerPath.substring(1);
                        }
                        dmImageUrl = dmServerUrl + dmAssetName;
                    }
                    useAssetDelivery = useAssetDelivery && StringUtils.isEmpty(policyDelegatePath);

                } else {
                    LOGGER.error("Unable to adapt resource '{}' used by image '{}' to an asset.", fileReference,
                        request.getResource().getPath());
                }
            } else {
                // handle the case where the image is not coming from DAM but from a different source (e.g. NGDM)
                if (!hasContent) {
                    LOGGER.error("Unable to find resource '{}' used by image '{}'.", fileReference, request.getResource().getPath());
                }
            }
        }
        if (hasContent) {
            disableLazyLoading = currentStyle.get(PN_DESIGN_LAZY_LOADING_ENABLED, true);

            if (dmImageUrl == null){
                if (useAssetDelivery) {
                    srcUriTemplate = AssetDeliveryHelper.getSrcUriTemplate(assetDelivery, resource, imageName, extension,
                        jpegQuality, SRC_URI_TEMPLATE_WIDTH_VAR_ASSET_DELIVERY);
                }

                if (StringUtils.isEmpty(srcUriTemplate)) {
                    String staticSelectors = selector;
                    if (smartSizes.length > 0) {
                        // only include the quality selector in the URL, if there are sizes configured
                        staticSelectors += DOT + jpegQuality;
                    }
                    srcUriTemplate = baseResourcePath + DOT + staticSelectors +
                        SRC_URI_TEMPLATE_WIDTH_VAR + DOT + extension +
                        (inTemplate ? templateRelativePath : hasExternalImageResource ? externalImageResourcePath : "") +
                        (lastModifiedDate > 0 ? ("/" + lastModifiedDate + (StringUtils.isNotBlank(imageName) ? ("/" + imageName) : "")) : "") +
                        (inTemplate || lastModifiedDate > 0 ? DOT + extension : "");
                }

                if (StringUtils.isNotBlank(policyDelegatePath)) {
                    srcUriTemplate += "?" + CONTENT_POLICY_DELEGATE_PATH + "=" + policyDelegatePath;
                    src += "?" + CONTENT_POLICY_DELEGATE_PATH + "=" + policyDelegatePath;
                }
            } else {
                srcUriTemplate = dmImageUrl;
                src = dmImageUrl;
                if (StringUtils.isNotBlank(smartCropRendition)) {
                    if(smartCropRendition.equals(SMART_CROP_AUTO)) {
                        srcUriTemplate += SRC_URI_TEMPLATE_WIDTH_VAR;
                    } else {
                        srcUriTemplate += "%3A" + smartCropRendition;
                        src += "%3A" + smartCropRendition;
                    }
                }
                if (smartSizes.length > 0 && StringUtils.isBlank(smartCropRendition)) {
                    String qualityCommand = "?qlt=" + jpegQuality;
                    srcUriTemplate += qualityCommand;
                    src += qualityCommand;
                    String widCommand;
                    if (smartSizes.length == 1) {
                        widCommand = "&wid=" + smartSizes[0];
                        srcUriTemplate += widCommand;
                        src += widCommand;
                    } else {
                        widCommand = "&wid=%7B.width%7D";
                        srcUriTemplate += widCommand;
                    }
                }
                if (lastModifiedDate > 0){
                    String timeStampCommand = (srcUriTemplate.contains("?") ? '&':'?') + "ts=" + lastModifiedDate;
                    srcUriTemplate += timeStampCommand;
                    src += timeStampCommand;
                }
                if (StringUtils.isNotBlank(imagePreset) && StringUtils.isBlank(smartCropRendition)){
                    String imagePresetCommand = (srcUriTemplate.contains("?") ? '&':'?') + "$" + imagePreset + "$";
                    srcUriTemplate += imagePresetCommand;
                    src += imagePresetCommand;
                }
                if (StringUtils.isNotBlank(imageModifiers)){
                    String imageModifiersCommand = (srcUriTemplate.contains("?") ? '&':'?') + imageModifiers;
                    srcUriTemplate += imageModifiersCommand;
                    src += imageModifiersCommand;
                }

                String dprParameter = "";
                // If DM is enabled, use smart imaging for smartcrop renditions
                if (getClass().equals(com.adobexp.aem.core.components.internal.models.ImageImpl.class) && isDmFeaturesEnabled && !StringUtils.isBlank(smartCropRendition)) {
                    dprParameter = (srcUriTemplate.contains("?") ? '&':'?') + "dpr=on,{dpr}";
                } else {
                    //add "dpr=off" parameter to image source url
                    dprParameter = (srcUriTemplate.contains("?") ? '&':'?') + "dpr=off";
                }

                srcUriTemplate += dprParameter;
                src += dprParameter;

                if (srcUriTemplate.equals(src)) {
                    srcUriTemplate = null;
                }
            }

            buildAreas();
            buildJson();

            disableLazyLoading = currentStyle.get(PN_DESIGN_LAZY_LOADING_ENABLED, false);
            imageLinkHidden = properties.get(PN_IMAGE_LINK_HIDDEN, imageLinkHidden);
            sizes = String.join((", "), currentStyle.get(PN_DESIGN_SIZES, new String[0]));
            disableLazyLoading = properties.get(PN_DESIGN_LAZY_LOADING_ENABLED, currentStyle.get(PN_DESIGN_LAZY_LOADING_ENABLED, false));
        }
    }

    /**
     * Extract the image name from the asset
     * @param asset the asset
     * @return the image name
     */
    protected String getImageNameFromAsset(Asset asset) {
    	return java.util.Optional.ofNullable(asset)
                .map(Asset::getName)
                .map(StringUtils::trimToNull)
                .map(FilenameUtils::getBaseName)
                .map(this::getSeoFriendlyName)
                .orElse(StringUtils.EMPTY);
    }

    /**
     * Content editors can store DAM assets with white spaces in the name, this
     * method makes the asset name SEO friendly, Translates the string into
     * {@code application/x-www-form-urlencoded} format using {@code utf-8} encoding
     * scheme.
     *
     * @param imageName The image name
     * @return the SEO friendly image name
     */
    protected String getSeoFriendlyName(String imageName) {
        return imageName.replaceAll(SEO_NAME_FILTER_PATTERN, "-").toLowerCase();
    }


    @Override
    public String getSrc() {
        return src;
    }

    @Override
    public boolean displayPopupTitle() {
        return displayPopupTitle;
    }

    @Override
    public String getAlt() {
        return alt;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getLink() {
        return link == null ? null : link.getURL();
    }

    @Override
    @JsonIgnore
    public String getFileReference() {
        return fileReference;
    }

    @Override
    @JsonIgnore
    @Deprecated
    public String getJson() {
        return json;
    }

    @Override
    @JsonIgnore
    public boolean isDecorative() {
        return this.isDecorative;
    }

    @SuppressWarnings({ "squid:CallToDeprecatedMethod", "deprecation" })
    protected void buildJson() {
        JsonArrayBuilder smartSizesJsonBuilder = Json.createArrayBuilder();
        for (int size : smartSizes) {
            smartSizesJsonBuilder.add(size);
        }
        JsonArrayBuilder smartImagesJsonBuilder = Json.createArrayBuilder();
        for (String image : smartImages) {
            smartImagesJsonBuilder.add(image);
        }
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        jsonObjectBuilder.add(JSON_SMART_IMAGES, smartImagesJsonBuilder);
        jsonObjectBuilder.add(JSON_SMART_SIZES, smartSizesJsonBuilder);
        jsonObjectBuilder.add(JSON_LAZY_ENABLED, !disableLazyLoading);
        json = jsonObjectBuilder.build().toString();
    }

    private Set<Integer> getSupportedRenditionWidths() {
        Set<Integer> allowedRenditionWidths = new TreeSet<>();
        String[] supportedWidthsConfig = currentStyle.get(PN_DESIGN_ALLOWED_RENDITION_WIDTHS, new String[0]);
        for (String width : supportedWidthsConfig) {
            try {
                allowedRenditionWidths.add(Integer.parseInt(width));
            } catch (NumberFormatException e) {
                LOGGER.error(String.format("Invalid width detected (%s) for content policy configuration.", width), e);
            }
        }
        return allowedRenditionWidths;
    }

    private boolean smartSizesSupported() {
        // "smart sizes" is supported for all images except SVG
        return !StringUtils.equals(mimeType, MIME_TYPE_IMAGE_SVG);
    }

    @Override
    public int @NotNull [] getWidths() {
        return Arrays.copyOf(smartSizes, smartSizes.length);
    }

    public boolean isDmImage() {
        return dmImage;
    }

    public String getSmartCropRendition() {
        return smartCropRendition;
    }

    @Override
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<ImageArea> getAreas() {
        if (areas == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(areas);
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @SuppressWarnings("deprecation")
    protected void buildAreas() {
        areas = new ArrayList<>();
        String mapProperty = properties.get(Image.PN_MAP, String.class);
        if (StringUtils.isNotEmpty(mapProperty)) {
            // Parse the image map areas as defined at {@code Image.PN_MAP}
            String[] mapAreas = StringUtils.split(mapProperty, "][");
            for (String area : mapAreas) {
                int coordinatesEndIndex = area.indexOf(')');
                if (coordinatesEndIndex < 0) {
                    break;
                }
                String shapeAndCoords = StringUtils.substring(area, 0, coordinatesEndIndex + 1);
                String shape = StringUtils.substringBefore(shapeAndCoords, "(");
                String coordinates = StringUtils.substringBetween(shapeAndCoords, "(", ")");
                String remaining = StringUtils.substring(area, coordinatesEndIndex + 1);
                String[] remainingTokens = StringUtils.split(remaining, "|");
                if (StringUtils.isBlank(shape) || StringUtils.isBlank(coordinates)) {
                    break;
                }
                if (remainingTokens.length > 0) {
                    String href = StringUtils.removeAll(remainingTokens[0], "\"");
                    String target = remainingTokens.length > 1 ? StringUtils.removeAll(remainingTokens[1], "\"") : "";

                    @SuppressWarnings("rawtypes")
                    Link link = linkManager.get(href).withLinkTarget(target).build();
                    if (!link.isValid()) {
                        break;
                    }

                    String alt = remainingTokens.length > 2 ? StringUtils.removeAll(remainingTokens[2], "\"") : "";
                    String relativeCoordinates = remainingTokens.length > 3 ? remainingTokens[3] : "";
                    relativeCoordinates = StringUtils.substringBetween(relativeCoordinates, "(", ")");
                    areas.add(newImageArea(shape, coordinates, relativeCoordinates, link, alt));
                }
            }
        }
    }

    protected ImageArea newImageArea(String shape, String coordinates, String relativeCoordinates, @SuppressWarnings("rawtypes") @NotNull Link link, String alt ) {
        return new ImageAreaImpl(shape, coordinates, relativeCoordinates, link, alt);
    }


    @SuppressWarnings("rawtypes")
    @Override
    @Nullable
    public Link getImageLink() {
        return (imageLinkHidden || (link != null && !link.isValid())) ? null : link;
    }


    @Override
    public String getSrcset() {

        if (!StringUtils.isEmpty(srcSet)) {
            return srcSet;
        }

        if (useAssetDelivery) {
            srcSet = AssetDeliveryHelper.getSrcSet(assetDelivery, resource, imageName, extension, smartSizes,
                jpegQuality);
            if (!StringUtils.isEmpty(srcSet)) {
                return srcSet;
            }
        }

        int[] widthsArray = getWidths();
        String srcUritemplate = getSrcUriTemplate();

        // handle srcset creation for auto smartcrop of remote assets
        if (ngdmImage && StringUtils.equals(smartCropRendition, SMART_CROP_AUTO) && client != null
            && srcUritemplate != null) {
            srcUritemplate = StringUtils.replace(srcUriTemplate, URI_WIDTH_PLACEHOLDER_ENCODED, URI_WIDTH_PLACEHOLDER);
            getRemoteAssetSrcset(srcUritemplate);
            return srcSet;
        }

        String[] srcsetArray = new String[widthsArray.length];
        if (widthsArray.length > 0 && srcUritemplate != null) {
            srcUritemplate = StringUtils.replace(srcUriTemplate, URI_WIDTH_PLACEHOLDER_ENCODED, URI_WIDTH_PLACEHOLDER);
            if (srcUritemplate.contains(URI_WIDTH_PLACEHOLDER)) {
                // in case of dm image and auto smartcrop the srcset needs to generated client side
                if (dmImage && StringUtils.equals(smartCropRendition, SMART_CROP_AUTO)) {
                    srcSet = EMPTY_PIXEL;
                } else {
                    for (int i = 0; i < widthsArray.length; i++) {
                        if (srcUritemplate.contains("=" + URI_WIDTH_PLACEHOLDER)) {
                            srcsetArray[i] =
                                srcUritemplate.replace("{.width}", String.format("%s", widthsArray[i])) + " " + widthsArray[i] + "w";
                        } else {
                            srcsetArray[i] =
                                srcUritemplate.replace("{.width}", String.format(".%s", widthsArray[i])) + " " + widthsArray[i] + "w";
                        }
                    }
                    srcSet = StringUtils.join(srcsetArray, ',');
                }
                return srcSet;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public String getSizes() {
        return sizes;
    }

    @Nullable
    @Override
    @JsonIgnore
    public String getHeight() {
        int height = getOriginalDimension().height;
        if (height > 0) {
            return String.valueOf(height);
        }
        return null;
    }

    @Nullable
    @Override
    @JsonIgnore
    public String getWidth() {
        int width = getOriginalDimension().width;
        if (width > 0) {
            return String.valueOf(width);
        }
        return null;
    }

    @Override
    @JsonIgnore
    public String getSrcUriTemplate() {
        if (ngdmImage) {
            return prepareNgdmSrcUriTemplate();
        }
        return srcUriTemplate;
    }

    @Override
    @JsonIgnore
    @Deprecated
    public int getLazyThreshold() {
        return 0;
    }

    protected void initResource() {
        resource = getWrappedImageResourceWithInheritance(resource, linkManager, currentStyle, currentPage);
    }

    @Override
    public boolean isLazyEnabled() {
        return !disableLazyLoading;
    }


    private Dimension getOriginalDimension() {
        if (this.dimension == null) {
            this.dimension = getOriginalDimensionInternal();
        }
        return this.dimension;
    }

    private void getRemoteAssetSrcset(String srcUritemplate) {
        String endPointUrl = "https://" + nextGenDynamicMediaConfig.getRepositoryId() + metadataDeliveryEndpoint;
        HttpGet get = new HttpGet(endPointUrl);
        get.setHeader("X-Adobe-Accept-Experimental", "1");
        ResponseHandler<String> responseHandler = new NextGenDMSrcsetBuilderResponseHandler();
        try {
            String response = client.execute(get, responseHandler);
            if (!StringUtils.isEmpty(response)) {
                JsonReader jsonReader = Json.createReader(new StringReader(response));
                JsonObject metadata = jsonReader.readObject();
                jsonReader.close();
                JsonObject repositoryMetadata = metadata.getJsonObject("repositoryMetadata");
                JsonObject smartCrops = repositoryMetadata.getJsonObject("smartcrops");
                String[] ngdmSrcsetArray = new String[smartCrops.size()];
                int i = 0;
                for (Map.Entry<String, JsonValue> entry : smartCrops.entrySet()) {
                    String namedSmartCrop = entry.getKey();
                    if (srcUritemplate.contains("=" + URI_WIDTH_PLACEHOLDER)) {
                        JsonValue smartCropWidth = smartCrops.getJsonObject(namedSmartCrop).get("width");
                        ngdmSrcsetArray[i] =
                            srcUritemplate.replace("width={.width}", String.format("smartcrop=%s", namedSmartCrop)) + " " + smartCropWidth.toString().replaceAll("\"", "") + "w";
                        i++;
                    }
                }
                srcSet = StringUtils.join(ngdmSrcsetArray, ',');
            }
        } catch (IOException | JsonException e) {
            LOGGER.warn("Couldn't generate srcset for remote asset");
        }
    }

    private Dimension getOriginalDimensionInternal() {
        ValueMap inheritedResourceProperties = resource.getValueMap();
        String inheritedFileReference = inheritedResourceProperties.get(DownloadResource.PN_REFERENCE, String.class);
        Asset asset;
        String resizeWidth = currentStyle.get(PN_DESIGN_RESIZE_WIDTH, String.class);
        if (StringUtils.isNotEmpty(inheritedFileReference)) {
            final Resource assetResource = request.getResourceResolver().getResource(inheritedFileReference);
            if (assetResource != null) {
                asset = assetResource.adaptTo(Asset.class);
                EnhancedRendition original = null;
                if (asset != null) {
                    original = new EnhancedRendition(asset.getOriginal());
                }
                if (original != null) {
                    Dimension dimension = original.getDimension();
                    if (dimension != null) {
                        if (resizeWidth != null && Integer.parseInt(resizeWidth) > 0 && Integer.parseInt(resizeWidth) < dimension.getWidth()) {
                            int calculatedHeight = (int) Math.round(Integer.parseInt(resizeWidth) * (dimension.getHeight() / (float) dimension.getWidth()));
                            return new Dimension(Integer.parseInt(resizeWidth), calculatedHeight);
                        }
                        return dimension;
                    }
                }
            }
        }
        return new Dimension(0, 0);
    }

    private boolean isNgdmSupportAvailable() {
        return nextGenDynamicMediaConfig != null && nextGenDynamicMediaConfig.enabled() &&
            StringUtils.isNotBlank(nextGenDynamicMediaConfig.getRepositoryId());
    }

    private void initNextGenerationDynamicMedia() {
        initResource();
        properties = resource.getValueMap();
        String fileReference = properties.get("fileReference", String.class);
        String smartCrop = properties.get("smartCropRendition", String.class);
        String modifiers = properties.get("imageModifiers", String.class);
        if (isNgdmImageReference(fileReference)) {
            int width = currentStyle.get(PN_DESIGN_RESIZE_WIDTH, DEFAULT_NGDM_ASSET_WIDTH);
            NextGenDMImageURIBuilder builder = new NextGenDMImageURIBuilder(nextGenDynamicMediaConfig, fileReference)
                .withPreferWebp(true)
                .withWidth(width);
            if(StringUtils.isNotEmpty(smartCrop) && !StringUtils.equals(smartCrop, SMART_CROP_AUTO)) {
                builder.withSmartCrop(smartCrop);
            }
            if (StringUtils.isNotEmpty(modifiers)) {
                builder.withImageModifiers(modifiers);
            }
            src = builder.build();
            ngdmImage = true;
            hasContent = true;
            if (clientBuilderFactory != null) {
                client = clientBuilderFactory.newBuilder().build();
            }
            metadataDeliveryEndpoint = nextGenDynamicMediaConfig.getAssetMetadataPath();
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(fileReference);
            scanner.useDelimiter("/");
            String assetId = scanner.next();
            metadataDeliveryEndpoint = metadataDeliveryEndpoint.replace(PATH_PLACEHOLDER_ASSET_ID, assetId);
        }
    }

    @NotNull
    private String prepareNgdmSrcUriTemplate() {
        // replace the value of the width URL parameter with the placeholder
        srcUriTemplate = src.replaceFirst("width=\\d+", "width=" + URI_WIDTH_PLACEHOLDER_ENCODED);
        String ret = src.replaceFirst("width=\\d+", "width=" + URI_WIDTH_PLACEHOLDER);
        return ret;
    }

    public static boolean isNgdmImageReference(String fileReference) {
        return StringUtils.isNotBlank(fileReference) && fileReference.startsWith("/urn:");
    }

}
