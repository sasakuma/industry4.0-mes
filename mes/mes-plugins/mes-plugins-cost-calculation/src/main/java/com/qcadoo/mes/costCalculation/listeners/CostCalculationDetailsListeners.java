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
package com.qcadoo.mes.costCalculation.listeners;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.costCalculation.CostCalculationService;
import com.qcadoo.mes.costCalculation.constants.CalculateMaterialCostsMode;
import com.qcadoo.mes.costCalculation.constants.CostCalculationFields;
import com.qcadoo.mes.costCalculation.constants.SourceOfMaterialCosts;
import com.qcadoo.mes.costCalculation.hooks.CostCalculationDetailsHooks;
import com.qcadoo.mes.costCalculation.print.CostCalculationReportService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrderType;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.tree.ProductStructureTreeService;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.google.common.base.Preconditions.checkArgument;

@Service
public class CostCalculationDetailsListeners {

    private static final String L_TECHNOLOGY = "technology";

    private static final String L_ORDER = "order";

    private static final String L_FORM = "form";

    private static final String L_MINIMAL_QUANTITY = "minimalQuantity";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private CostCalculationService costCalculationService;

    @Autowired
    private CostCalculationReportService costCalculationReportService;

    @Autowired
    private CostCalculationDetailsHooks costCalculationDetailsHooks;

    @Autowired
    private ProductStructureTreeService productStructureTreeService;

    public void generateCostCalculation(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        state.performEvent(view, "save", new String[0]);

        if (state.isHasError()) {
            return;
        }

        Entity costCalculation = getEntityFromForm(view);
        attachBelongsToFields(costCalculation);
        LookupComponent technologyLookup = (LookupComponent) view.getComponentByReference(CostCalculationFields.TECHNOLOGY);
        productStructureTreeService.generateProductStructureTree(null, technologyLookup.getEntity());
        costCalculation = costCalculationService.calculateTotalCost(costCalculation);

        costCalculationService.calculateSellPriceOverhead(costCalculation);
        costCalculationService.calculateSellPrice(costCalculation);

        fillFields(view, costCalculation);

        costCalculationReportService.generateCostCalculationReport(view, state, args);

        view.getComponentByReference(L_FORM).addMessage("costCalculation.messages.success.calculationComplete",
                MessageType.SUCCESS);
    }

    private Entity getEntityFromForm(final ViewDefinitionState view) {
        FormComponent costCalculationForm = (FormComponent) view.getComponentByReference(L_FORM);
        checkArgument(costCalculationForm != null, "form is null");
        checkArgument(costCalculationForm.isValid(), "invalid form");

        Long costCalculationId = costCalculationForm.getEntityId();

        return costCalculationForm.getEntity().getDataDefinition().get(costCalculationId);
    }

    private void attachBelongsToFields(final Entity costCalculation) {
        final Map<String, DataDefinition> belongsToFieldDDs = Maps.newHashMap();

        belongsToFieldDDs.put(CostCalculationFields.ORDER,
                dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_ORDER));
        belongsToFieldDDs.put(CostCalculationFields.TECHNOLOGY,
                dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY));
        belongsToFieldDDs.put(CostCalculationFields.DEFAULT_TECHNOLOGY,
                dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY));
        belongsToFieldDDs.put(CostCalculationFields.PRODUCT,
                dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_PRODUCT));

        for (Map.Entry<String, DataDefinition> belongsToFieldDD : belongsToFieldDDs.entrySet()) {
            Object fieldValue = costCalculation.getField(belongsToFieldDD.getKey());

            if (!(fieldValue instanceof Long)) {
                continue;
            }

            Entity fieldEntity = belongsToFieldDD.getValue().get((Long) fieldValue);
            costCalculation.setField(belongsToFieldDD.getKey(), fieldEntity);
        }
    }

    private void fillFields(final ViewDefinitionState view, final Entity costCalculation) {
        final Set<String> costFields = Sets.newHashSet(CostCalculationFields.PRODUCTION_COST_MARGIN_VALUE,
                CostCalculationFields.MATERIAL_COST_MARGIN_VALUE, CostCalculationFields.TOTAL_OVERHEAD,
                CostCalculationFields.TOTAL_MATERIAL_COSTS, CostCalculationFields.TOTAL_MACHINE_HOURLY_COSTS,
                CostCalculationFields.TOTAL_LABOR_HOURLY_COSTS, CostCalculationFields.TOTAL_PIECEWORK_COSTS,
                CostCalculationFields.TOTAL_TECHNICAL_PRODUCTION_COSTS, CostCalculationFields.TOTAL_COSTS,
                CostCalculationFields.TOTAL_COST_PER_UNIT, CostCalculationFields.ADDITIONAL_OVERHEAD_VALUE,
                CostCalculationFields.REGISTRATION_PRICE_OVERHEAD_VALUE, CostCalculationFields.PROFIT_VALUE,
                CostCalculationFields.SELL_PRICE_VALUE, CostCalculationFields.TECHNICAL_PRODUCTION_COSTS);

        for (String costField : costFields) {
            FieldComponent fieldComponent = (FieldComponent) view.getComponentByReference(costField);
            fieldComponent.setFieldValue(numberService.setScaleWithDefaultMathContext(
                    BigDecimalUtils.convertNullToZero(costCalculation.getDecimalField(costField)), 2));
        }
    }

    private void clearFieldValues(final ViewDefinitionState view) {
        view.getComponentByReference(CostCalculationFields.ORDER).addMessage("costCalculation.messages.lackOfTechnology",
                MessageType.FAILURE);
        view.getComponentByReference(CostCalculationFields.DEFAULT_TECHNOLOGY).setFieldValue(null);
        view.getComponentByReference(CostCalculationFields.TECHNOLOGY).setFieldValue(null);
        view.getComponentByReference(CostCalculationFields.QUANTITY).setFieldValue(null);
        view.getComponentByReference(CostCalculationFields.PRODUCT).setFieldValue(null);
        view.getComponentByReference(CostCalculationFields.PRODUCTION_LINE).setFieldValue(null);
    }

    public void printCostCalculationReport(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        costCalculationReportService.printCostCalculationReport(view, state, args);
    }

    public void fillFieldWhenOrderChanged(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (!(state instanceof FieldComponent)) {
            return;
        }

        LookupComponent orderLookup = (LookupComponent) view.getComponentByReference(CostCalculationFields.ORDER);

        Entity order = orderLookup.getEntity();

        if (order == null) {
            return;
        }

        Entity technology = getTechnologyFromOrder(order);

        if (technology == null) {
            return;
        }

        costCalculationDetailsHooks.applyValuesToFields(view, technology, order);
    }

    public void changeOrderProduct(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (!(state instanceof LookupComponent)) {
            return;
        }

        LookupComponent productLookup = (LookupComponent) state;
        LookupComponent orderLookup = (LookupComponent) view.getComponentByReference(CostCalculationFields.ORDER);
        LookupComponent defaultTechnologyLookup = (LookupComponent) view
                .getComponentByReference(CostCalculationFields.DEFAULT_TECHNOLOGY);
        LookupComponent technologyLookup = (LookupComponent) view.getComponentByReference(CostCalculationFields.TECHNOLOGY);

        Entity product = productLookup.getEntity();
        Entity order = orderLookup.getEntity();
        Entity technology = technologyLookup.getEntity();

        if (product != null) {
            fillCostPerUnitUnitField(view, state, args);

            if (order != null) {
                Entity orderProduct = order.getBelongsToField(OrderFields.PRODUCT);

                if (!product.getId().equals(orderProduct.getId())) {
                    orderLookup.setFieldValue(null);
                }
            }

            Entity defaultTechnology = getDefaultTechnology(product);

            if (defaultTechnology == null) {
                defaultTechnologyLookup.setFieldValue(null);
            } else {
                defaultTechnologyLookup.setFieldValue(defaultTechnology.getId());
            }

            if (technology == null) {
                if (defaultTechnology != null) {
                    technologyLookup.setFieldValue(defaultTechnology.getId());
                }
            } else {
                Entity technologyProduct = technology.getBelongsToField(TechnologyFields.PRODUCT);

                if (!product.getId().equals(technologyProduct.getId())) {
                    if (defaultTechnology == null) {
                        technologyLookup.setFieldValue(null);
                    } else {
                        technologyLookup.setFieldValue(defaultTechnology.getId());
                    }
                }
            }
        }

        orderLookup.requestComponentUpdateState();
        defaultTechnologyLookup.requestComponentUpdateState();
        technologyLookup.requestComponentUpdateState();
    }

    private Entity getTechnologyFromOrder(final Entity order) {
        Entity technology = null;

        String orderType = order.getStringField(OrderFields.ORDER_TYPE);

        if (OrderType.WITH_PATTERN_TECHNOLOGY.getStringValue().equals(orderType)) {
            technology = order.getBelongsToField(OrderFields.TECHNOLOGY_PROTOTYPE);
        } else {
            technology = order.getBelongsToField(OrderFields.TECHNOLOGY);
        }

        return technology;
    }

    private Entity getDefaultTechnology(final Entity product) {
        DataDefinition technologyDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_TECHNOLOGY);

        SearchCriteriaBuilder searchCriteriaBuilder = technologyDD.find()
                .add(SearchRestrictions.eq(TechnologyFields.MASTER, true))
                .add(SearchRestrictions.belongsTo(TechnologyFields.PRODUCT, product));

        return searchCriteriaBuilder.setMaxResults(1).uniqueResult();
    }

    public void fillCostPerUnitUnitField(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        costCalculationDetailsHooks.fillCostPerUnitUnitField(view);
    }

    public void fillFieldWhenTechnologyChanged(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (!(state instanceof FieldComponent)) {
            return;
        }

        LookupComponent technologyLookup = (LookupComponent) view.getComponentByReference(CostCalculationFields.TECHNOLOGY);

        Entity technology = technologyLookup.getEntity();

        if (technology == null) {
            return;
        }

       costCalculationDetailsHooks.applyValuesToFields(view, technology, null);
    }

    public void ifCurrentGlobalIsSelected(final ViewDefinitionState view, final ComponentState state, final String[] args) {

        FieldComponent sourceOfMaterialCosts = (FieldComponent) view
                .getComponentByReference(CostCalculationFields.SOURCE_OF_MATERIAL_COSTS);
        FieldComponent calculateMaterialCostsMode = (FieldComponent) view
                .getComponentByReference(CostCalculationFields.CALCULATE_MATERIAL_COSTS_MODE);

        if (SourceOfMaterialCosts.CURRENT_GLOBAL_DEFINITIONS_IN_PRODUCT.getStringValue().equals(
                sourceOfMaterialCosts.getFieldValue())
                && (CalculateMaterialCostsMode.COST_FOR_ORDER.getStringValue().equals(calculateMaterialCostsMode.getFieldValue()))) {
            sourceOfMaterialCosts.addMessage("costCalculation.messages.optionUnavailable", MessageType.FAILURE);
        }
    }

    public void saveNominalCosts(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent formComponent = (FormComponent) view.getComponentByReference(L_FORM);
        Entity costsEntity = formComponent.getEntity();
        Entity product = costsEntity.getBelongsToField(BasicConstants.MODEL_PRODUCT);
        BigDecimal tkw = costsEntity.getDecimalField(CostCalculationFields.TECHNICAL_PRODUCTION_COSTS);
        product.setField("nominalCost", numberService.setScaleWithDefaultMathContext(tkw));
        Entity savedEntity = product.getDataDefinition().save(product);
        if (!savedEntity.isValid()) {
            view.getComponentByReference(L_FORM).addMessage("costCalculation.messages.success.saveCostsFailure",
                    MessageType.FAILURE);
        }

        view.getComponentByReference(L_FORM).addMessage("costCalculation.messages.success.saveCostsSuccess", MessageType.SUCCESS);

    }

}
