/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.technologies;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.qcadoo.mes.technologies.constants.OperationFields;
import com.qcadoo.mes.technologies.constants.OperationProductInComponentFields;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.mes.technologies.states.constants.TechnologyState;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchQueryBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.plugin.api.PluginAccessor;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.utils.NumberGeneratorService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TechnologyService {

    private static final String L_FORM = "form";

    private static final String L_PRODUCT = "product";

    private static final String L_QUANTITY = "quantity";

    private static final String L_OPERATION = "operation";

    private static final String L_OPERATION_COMPONENT = "operationComponent";

    public static final String L_01_COMPONENT = "01component";

    public static final String L_02_INTERMEDIATE = "02intermediate";

    public static final String L_03_FINAL_PRODUCT = "03finalProduct";

    public static final String L_04_WASTE = "04waste";

    public static final String L_00_UNRELATED = "00unrelated";

    private static final String IS_SYNCHRONIZED_QUERY = String.format(
            "SELECT t.id as id, t.%s as %s from #%s_%s t where t.id = :technologyId", TechnologyFields.EXTERNAL_SYNCHRONIZED,
            TechnologyFields.EXTERNAL_SYNCHRONIZED, TechnologiesConstants.PLUGIN_IDENTIFIER,
            TechnologiesConstants.MODEL_TECHNOLOGY);

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberGeneratorService numberGeneratorService;

    @Autowired
    private ProductQuantitiesService productQuantitiesService;

    @Autowired
    private PluginAccessor pluginAccessor;

    @Autowired
    private TechnologyNameAndNumberGenerator technologyNameAndNumberGenerator;

    public void copyCommentAndAttachmentFromLowerInstance(final Entity technologyOperationComponent, final String belongsToName) {
        Entity operation = technologyOperationComponent.getBelongsToField(belongsToName);

        if (operation != null) {
            technologyOperationComponent.setField(TechnologyOperationComponentFields.COMMENT,
                    operation.getStringField(OperationFields.COMMENT));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.ATTACHMENT,
                    operation.getStringField(OperationFields.ATTACHMENT));
        }
    }

    public boolean checkIfTechnologyStateIsOtherThanCheckedAndAccepted(final Entity technology) {
        String state = technology.getStringField(TechnologyFields.STATE);

        return !TechnologyState.ACCEPTED.getStringValue().equals(state)
                && !TechnologyState.CHECKED.getStringValue().equals(state);
    }

    public boolean isTechnologyUsedInActiveOrder(final Entity technology) {
        if (!ordersPluginIsEnabled()) {
            return false;
        }

        SearchCriteriaBuilder searchCriteria = getOrderDataDefinition().find();
        searchCriteria.add(SearchRestrictions.belongsTo("technology", technology));
        searchCriteria.add(SearchRestrictions.in("state",
                Lists.newArrayList("01pending", "02accepted", "03inProgress", "06interrupted")));
        searchCriteria.setMaxResults(1);

        return searchCriteria.uniqueResult() != null;
    }

    private boolean ordersPluginIsEnabled() {
        return pluginAccessor.getPlugin("orders") != null;
    }

    private DataDefinition getOrderDataDefinition() {
        return dataDefinitionService.get("orders", "order");
    }

    private enum ProductDirection {
        IN, OUT;
    }

    public void generateTechnologyGroupNumber(final ViewDefinitionState viewDefinitionState) {
        numberGeneratorService.generateAndInsertNumber(viewDefinitionState, TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_TECHNOLOGY_GROUP, L_FORM, TechnologyFields.NUMBER);
    }

    public void generateTechnologyNumber(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (!(state instanceof FieldComponent)) {
            throw new IllegalStateException("component is not FieldComponentState");
        }

        FieldComponent numberField = (FieldComponent) view.getComponentByReference(TechnologyFields.NUMBER);
        LookupComponent productLookup = (LookupComponent) state;

        Entity product = productLookup.getEntity();

        if (product == null || StringUtils.isNotEmpty(Objects.toString(numberField.getFieldValue(), ""))) {
            return;
        }
        numberField.setFieldValue(technologyNameAndNumberGenerator.generateNumber(product));
        numberField.requestComponentUpdateState();
    }

    public void generateTechnologyName(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (!(state instanceof FieldComponent)) {
            throw new IllegalStateException("component is not FieldComponentState");
        }

        FieldComponent nameField = (FieldComponent) view.getComponentByReference(TechnologyFields.NAME);
        LookupComponent productLookup = (LookupComponent) state;

        Entity product = productLookup.getEntity();

        if (product == null || StringUtils.isNotEmpty(Objects.toString(nameField.getFieldValue(), ""))) {
            return;
        }
        nameField.setFieldValue(technologyNameAndNumberGenerator.generateName(product));
        nameField.requestComponentUpdateState();
    }

    public void setLookupDisableInTechnologyOperationComponent(final ViewDefinitionState viewDefinitionState) {
        FormComponent form = (FormComponent) viewDefinitionState.getComponentByReference(L_FORM);
        FieldComponent operationLookup = (FieldComponent) viewDefinitionState.getComponentByReference(L_OPERATION);

        operationLookup.setEnabled(form.getEntityId() == null);
    }

    public void toggleDetailsViewEnabled(final ViewDefinitionState view) {
        view.getComponentByReference(TechnologyFields.STATE).performEvent(view, "toggleEnabled");
    }

    private boolean productComponentsContainProduct(final List<Entity> components, final Entity product) {
        boolean contains = false;

        for (Entity entity : components) {
            if (entity.getBelongsToField(L_PRODUCT).getId().equals(product.getId())) {
                contains = true;
                break;
            }
        }

        return contains;
    }

    private SearchCriteriaBuilder createSearchCriteria(final Entity product, final Entity technology,
            final ProductDirection direction) {
        String model = direction.equals(ProductDirection.IN) ? TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT
                : TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT;

        DataDefinition dd = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, model);

        SearchCriteriaBuilder search = dd.find();
        search.add(SearchRestrictions.eq("product.id", product.getId()));
        search.createAlias(L_OPERATION_COMPONENT, L_OPERATION_COMPONENT);
        search.add(SearchRestrictions.belongsTo("operationComponent.technology", technology));

        return search;
    }

    public String getProductType(final Entity product, final Entity technology) {
        SearchCriteriaBuilder searchIns = createSearchCriteria(product, technology, ProductDirection.IN);
        SearchCriteriaBuilder searchOuts = createSearchCriteria(product, technology, ProductDirection.OUT);
        SearchCriteriaBuilder searchOutsForRoots = createSearchCriteria(product, technology, ProductDirection.OUT);
        searchOutsForRoots.add(SearchRestrictions.isNull("operationComponent.parent"));

        boolean goesIn = productComponentsContainProduct(searchIns.list().getEntities(), product);
        boolean goesOut = productComponentsContainProduct(searchOuts.list().getEntities(), product);
        boolean goesOutInAroot = productComponentsContainProduct(searchOutsForRoots.list().getEntities(), product);

        if (goesOutInAroot) {
            if (technology.getBelongsToField(L_PRODUCT).getId().equals(product.getId())) {
                return L_03_FINAL_PRODUCT;
            } else {
                return L_04_WASTE;
            }
        }

        if (goesIn && !goesOut) {
            return L_01_COMPONENT;
        }

        if (goesIn && goesOut) {
            return L_02_INTERMEDIATE;
        }

        if (!goesIn && goesOut) {
            return L_04_WASTE;
        }

        return L_00_UNRELATED;
    }

    public boolean invalidateIfAllreadyInTheSameOperation(final DataDefinition operationProductComponentDD,
            final Entity operationProductComponent) {

        Entity product = operationProductComponent.getBelongsToField(L_PRODUCT);
        Entity operationComponent = operationProductComponent.getBelongsToField(L_OPERATION_COMPONENT);

        String fieldName;

        if (TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT.equals(operationProductComponentDD.getName())) {
            fieldName = TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS;
        } else {
            fieldName = TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS;
        }

        List<Entity> products = operationComponent.getHasManyField(fieldName);

        if (product == null || product.getId() == null) {
            throw new IllegalStateException("Cant get product id");
        }

        if (products != null && listContainsProduct(products, product, operationProductComponent)) {
            operationProductComponent.addError(operationProductComponentDD.getField(L_PRODUCT),
                    "technologyOperationComponent.validate.error.productAlreadyExistInTechnologyOperation");

            return false;
        }
        String oppositeFieldName;
        String errorMessage;
        if (TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT.equals(operationProductComponentDD.getName())) {
            oppositeFieldName = TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS;
            errorMessage = "technologyOperationComponent.validate.error.outProductAlreadyExistInTechnologyOperation";
        } else {
            oppositeFieldName = TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS;
            errorMessage = "technologyOperationComponent.validate.error.inProductAlreadyExistInTechnologyOperation";
        }
        List<Entity> oppositeProducts = operationComponent.getHasManyField(oppositeFieldName);
        if (oppositeProducts != null && listContainsProduct(oppositeProducts, product, operationProductComponent)) {
            operationProductComponent.addError(operationProductComponentDD.getField(L_PRODUCT), errorMessage);

            return false;
        }
        return true;
    }

    public Entity getTechnologyForProduct(final Entity product) {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY)
                .find().add(SearchRestrictions.belongsTo(TechnologyFields.PRODUCT, product))
                .add(SearchRestrictions.isNull(TechnologyFields.TECHNOLOGY_TYPE))
                .add(SearchRestrictions.eq(TechnologyFields.STATE, TechnologyState.ACCEPTED.getStringValue()))
                .add(SearchRestrictions.eq(TechnologyFields.MASTER, true)).uniqueResult();
    }

    private boolean listContainsProduct(final List<Entity> list, final Entity product, final Entity operationProductComponent) {
        Predicate<Entity> condition;
        if (operationProductComponent.getId() == null) {
            condition = opProduct -> opProduct.getBelongsToField(L_PRODUCT).getId().equals(product.getId());
        } else {
            condition = opProduct -> !opProduct.getId().equals(operationProductComponent.getId())
                    && opProduct.getBelongsToField(L_PRODUCT).getId().equals(product.getId());
        }
        return list.stream().anyMatch(condition);
    }

    public Optional<Entity> tryGetMainOutputProductComponent(Entity technologyOperationComponent) {

        try {
            Entity component = getMainOutputProductComponent(technologyOperationComponent);
            return Optional.of(component);
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * @param technologyOperationComponent
     * @return productOutComponent. Assuming operation can have only one product/intermediate.
     */
    // TODO dev_team introduce MainTocOutputProductProvider
    public Entity getMainOutputProductComponent(Entity technologyOperationComponent) {
        Entity parentTechnologyOperationComponent = technologyOperationComponent
                .getBelongsToField(TechnologyOperationComponentFields.PARENT);

        List<Entity> operationProductOutComponents = technologyOperationComponent
                .getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS);

        if (parentTechnologyOperationComponent == null) {
            Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
            Entity product = technology.getBelongsToField(TechnologyFields.PRODUCT);

            for (Entity operationProductOutComponent : operationProductOutComponents) {
                if (operationProductOutComponent.getBelongsToField(L_PRODUCT).getId().equals(product.getId())) {
                    return operationProductOutComponent;
                }
            }
        } else {
            List<Entity> operationProductInComponents = parentTechnologyOperationComponent
                    .getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS);

            for (Entity operationProductOutComponent : operationProductOutComponents) {
                for (Entity operationProductInComponent : operationProductInComponents) {
                    if (operationProductOutComponent.getBelongsToField(L_PRODUCT).getId()
                            .equals(operationProductInComponent.getBelongsToField(L_PRODUCT).getId())) {
                        return operationProductOutComponent;
                    }
                }
            }
        }

        throw new IllegalStateException("OperationComponent doesn't have any products nor intermediates, id = "
                + technologyOperationComponent.getId());
    }

    /**
     * @param operationComponent
     * @return Quantity of the output product associated with this operationComponent. Assuming operation can have only one
     *         product/intermediate.
     */
    public BigDecimal getProductCountForOperationComponent(final Entity operationComponent) {
        return getMainOutputProductComponent(operationComponent).getDecimalField(L_QUANTITY);
    }

    /**
     * Check if technology with given id is external synchronized.
     * 
     * @param technologyId
     *            identifier of the queried technology
     * @return true if technology is external synchronized
     */
    public boolean isExternalSynchronized(final Long technologyId) {
        SearchQueryBuilder sqb = getTechnologyDataDefinition().find(IS_SYNCHRONIZED_QUERY);
        sqb.setLong("technologyId", technologyId).setMaxResults(1);
        Entity projection = sqb.uniqueResult();
        Preconditions.checkNotNull(projection, String.format("Technology with id = '%s' does not exists.", technologyId));
        return projection.getBooleanField(TechnologyFields.EXTERNAL_SYNCHRONIZED);
    }

    private DataDefinition getTechnologyDataDefinition() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY);
    }

    public boolean isIntermediateProduct(final Entity opic) {
        boolean isIntermediate = false;

        Entity toc = opic.getBelongsToField(OperationProductInComponentFields.OPERATION_COMPONENT);

        List<Entity> childs = toc.getDataDefinition().find()
                .add(SearchRestrictions.belongsTo(TechnologyOperationComponentFields.PARENT, toc)).list().getEntities();

        if (childs.isEmpty()) {
            return isIntermediate;
        }

        long productId = opic.getBelongsToField(OperationProductInComponentFields.PRODUCT).getId();
        for (Entity child : childs) {
            for (Entity childOPOC : child.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS)) {
                long childProductId = childOPOC.getBelongsToField(OperationProductOutComponentFields.PRODUCT).getId();
                if (productId == childProductId) {
                    isIntermediate = true;
                    return isIntermediate;
                }
            }
        }
        return isIntermediate;
    }

    public boolean isFinalProduct(final Entity opoc) {
        boolean isFinalProduct = false;

        Entity toc = opoc.getBelongsToField(OperationProductInComponentFields.OPERATION_COMPONENT);

        if (toc.getBelongsToField(TechnologyOperationComponentFields.PARENT) != null) {
            return isFinalProduct;
        }

        Entity technology = toc.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);

        if (technology.getBelongsToField(TechnologyFields.PRODUCT).getId()
                .equals(opoc.getBelongsToField(OperationProductOutComponentFields.PRODUCT).getId())) {
            isFinalProduct = true;
        }

        return isFinalProduct;
    }

    public List<Entity> findComponentsForTechnology(final Long technologyId) {
        String query = "select DISTINCT opic.id as opicId, " + "opic.product as product, " + "(select count(*) from "
                + "#technologies_operationProductOutComponent opoc " + "left join opoc.operationComponent oc  "
                + "left join oc.technology as tech " + "left join oc.parent par  " + "where "
                + "opoc.product = inputProd and par.id = toc.id ) as isIntermediate "
                + "from #technologies_operationProductInComponent opic " + "left join opic.product as inputProd "
                + "left join opic.operationComponent toc " + "left join toc.technology tech " + "where tech.id = :technologyId ";
        List<Entity> allProducts = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT)
                .find(query).setLong("technologyId", technologyId).list().getEntities();

        List<Entity> components = allProducts.stream().filter(p -> (Long) p.getField("isIntermediate") == 0l)
                .map(cmp -> cmp.getBelongsToField("product")).collect(Collectors.toList());
        return components;
    }
}
