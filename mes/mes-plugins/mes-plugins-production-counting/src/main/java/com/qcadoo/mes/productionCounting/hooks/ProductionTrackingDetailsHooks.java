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
package com.qcadoo.mes.productionCounting.hooks;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.states.constants.OrderStateStringValues;
import com.qcadoo.mes.productionCounting.ProductionCountingService;
import com.qcadoo.mes.productionCounting.ProductionTrackingService;
import com.qcadoo.mes.productionCounting.constants.OrderFieldsPC;
import com.qcadoo.mes.productionCounting.constants.ProductionCountingConstants;
import com.qcadoo.mes.productionCounting.constants.ProductionTrackingFields;
import com.qcadoo.mes.productionCounting.listeners.ProductionTrackingDetailsListeners;
import com.qcadoo.mes.productionCounting.states.constants.ProductionTrackingState;
import com.qcadoo.mes.productionCounting.states.constants.ProductionTrackingStateStringValues;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.WindowComponent;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;
import com.qcadoo.view.api.ribbon.RibbonActionItem;
import com.qcadoo.view.api.ribbon.RibbonGroup;

@Service
public class ProductionTrackingDetailsHooks {

    private static final String L_FORM = "form";

    private static final String L_STATE = "state";

    private static final String L_IS_DISABLED = "isDisabled";

    private static final String L_WINDOW = "window";

    private static final String L_ACTIONS = "actions";

    private static final String L_PRODUCTS_QUANTITIES = "productsQuantities";

    private static final String L_PRODUCTION_COUNTING_QUANTITIES = "productionCountingQuantities";

    private static final String L_ANOMALIES = "anomalies";

    private static final String L_COPY = "copy";

    private static final String L_COPY_PLANNED_QUANTITY_TO_USED_QUANTITY = "copyPlannedQuantityToUsedQuantity";

    private static final String L_ADD_TO_ANOMALIES_LIST = "addToAnomaliesList";

    private static final String L_CORRECT = "correct";

    private static final String L_CORRECTION = "correction";

    private static final String L_CORRECTS = "corrects";

    private static final String L_PRODUCTS_TAB = "productsTab";

    private static final List<String> L_PRODUCTION_TRACKING_FIELD_NAMES = Lists.newArrayList(ProductionTrackingFields.ORDER,
            ProductionTrackingFields.TECHNOLOGY_OPERATION_COMPONENT, ProductionTrackingFields.STAFF,
            ProductionTrackingFields.SHIFT, ProductionTrackingFields.WORKSTATION, ProductionTrackingFields.DIVISION,
            ProductionTrackingFields.LABOR_TIME, ProductionTrackingFields.MACHINE_TIME,
            ProductionTrackingFields.EXECUTED_OPERATION_CYCLES, ProductionTrackingFields.TIME_RANGE_FROM,
            ProductionTrackingFields.TIME_RANGE_TO, ProductionTrackingFields.TRACKING_OPERATION_PRODUCT_IN_COMPONENTS,
            ProductionTrackingFields.TRACKING_OPERATION_PRODUCT_OUT_COMPONENTS, ProductionTrackingFields.SHIFT_START_DAY,
            ProductionTrackingFields.STAFF_WORK_TIMES);

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private ProductionCountingService productionCountingService;

    @Autowired
    private ProductionTrackingService productionTrackingService;

    @Autowired
    private ProductionTrackingDetailsListeners productionTrackingDetailsListeners;

    public void onBeforeRender(final ViewDefinitionState view) {
        FormComponent productionTrackingForm = (FormComponent) view.getComponentByReference(L_FORM);

        setCriteriaModifierParameters(view);

        productionTrackingService.fillProductionLineLookup(view);

        if (productionTrackingForm.getEntityId() == null) {
            setStateFieldValueToDraft(view);
        } else {
            Entity productionTracking = getProductionTrackingFromDB(productionTrackingForm.getEntityId());

            initializeProductionTrackingDetailsView(view);
            showLastStateChangeFailNotification(productionTrackingForm, productionTracking);
            changeFieldComponentsEnabledAndGridsEditable(view);
            updateRibbonState(view);
            toggleCorrectButton(view, productionTracking);
            toggleCorrectionFields(view, productionTracking);
            fetchNumberFromDatabase(view, productionTracking);
        }
    }

    private void fetchNumberFromDatabase(final ViewDefinitionState view, final Entity productionTracking) {
        FieldComponent numberField = (FieldComponent) view.getComponentByReference(ProductionTrackingFields.NUMBER);

        if (Strings.isNullOrEmpty((String) numberField.getFieldValue())) {
            numberField.setFieldValue(productionTracking.getStringField(ProductionTrackingFields.NUMBER));
        }
    }

    private void toggleCorrectButton(ViewDefinitionState view, Entity entity) {
        WindowComponent window = (WindowComponent) view.getComponentByReference(L_WINDOW);
        RibbonActionItem correctButton = window.getRibbon().getGroupByName(L_CORRECTION).getItemByName(L_CORRECT);

        String state = entity.getStringField(ProductionTrackingFields.STATE);
        Entity order = entity.getBelongsToField(ProductionTrackingFields.ORDER);
        String orderState = order.getStringField(OrderFields.STATE);

        boolean productionTrackingIsAccepted = ProductionTrackingStateStringValues.ACCEPTED.equals(state);
        boolean orderIsNotFinished = !OrderStateStringValues.COMPLETED.equals(orderState)
                && !OrderStateStringValues.ABANDONED.equals(orderState);

        if (productionTrackingIsAccepted && orderIsNotFinished) {
            correctButton.setEnabled(true);
        } else {
            correctButton.setEnabled(false);
        }

        correctButton.requestUpdate(true);
    }

    private void toggleCorrectionFields(ViewDefinitionState view, Entity entity) {
        Entity correctedProductionTracking = getCorrectedProductionTracking(entity);

        if (correctedProductionTracking != null) {
            view.getComponentByReference(ProductionTrackingFields.ORDER).setEnabled(false);
            view.getComponentByReference(OrderFields.PRODUCTION_LINE).setEnabled(false);
            view.getComponentByReference(ProductionTrackingFields.TECHNOLOGY_OPERATION_COMPONENT).setEnabled(false);

            view.getComponentByReference(L_CORRECTS).setVisible(true);
            view.getComponentByReference(L_CORRECTS)
                    .setFieldValue(correctedProductionTracking.getStringField(ProductionTrackingFields.NUMBER));
        }
    }

    private Entity getCorrectedProductionTracking(Entity entity) {
        return entity.getDataDefinition().find().add(SearchRestrictions.belongsTo(ProductionTrackingFields.CORRECTION, entity))
                .uniqueResult();
    }

    public void setCriteriaModifierParameters(final ViewDefinitionState view) {
        FormComponent productionTrackingForm = (FormComponent) view.getComponentByReference(L_FORM);
        LookupComponent technologyOperationComponentLookup = (LookupComponent) view
                .getComponentByReference(ProductionTrackingFields.TECHNOLOGY_OPERATION_COMPONENT);

        Entity productionTracking = productionTrackingForm.getEntity();

        Entity order = productionTracking.getBelongsToField(ProductionTrackingFields.ORDER);

        if (order != null) {
            Entity technology = order.getBelongsToField(OrderFields.TECHNOLOGY);

            if (technology != null) {
                FilterValueHolder filterValueHolder = technologyOperationComponentLookup.getFilterValue();
                filterValueHolder.put(OrderFields.TECHNOLOGY, technology.getId());

                technologyOperationComponentLookup.setFilterValue(filterValueHolder);
            }
        }
    }

    private void setStateFieldValueToDraft(final ViewDefinitionState view) {
        FieldComponent stateField = (FieldComponent) view.getComponentByReference(L_STATE);

        stateField.setFieldValue(ProductionTrackingState.DRAFT.getStringValue());
        stateField.requestComponentUpdateState();
    }

    private Entity getProductionTrackingFromDB(final Long productionTrackingId) {
        return dataDefinitionService
                .get(ProductionCountingConstants.PLUGIN_IDENTIFIER, ProductionCountingConstants.MODEL_PRODUCTION_TRACKING)
                .get(productionTrackingId);
    }

    public void initializeProductionTrackingDetailsView(final ViewDefinitionState view) {
        FormComponent productionTrackingForm = (FormComponent) view.getComponentByReference(L_FORM);

        FieldComponent stateField = (FieldComponent) view.getComponentByReference(ProductionTrackingFields.STATE);
        LookupComponent orderLookup = (LookupComponent) view.getComponentByReference(ProductionTrackingFields.ORDER);
        FieldComponent isDisabledField = (FieldComponent) view.getComponentByReference(L_IS_DISABLED);

        Entity productionTracking = productionTrackingForm.getEntity();

        stateField.setFieldValue(productionTracking.getField(ProductionTrackingFields.STATE));
        stateField.requestComponentUpdateState();

        Entity order = orderLookup.getEntity();

        isDisabledField.setFieldValue(false);

        if (order != null) {
            changeProductsTabVisible(view, productionTracking, order);

            productionTrackingService.setTimeAndPieceworkComponentsVisible(view, order);
        }
    }

    private void changeProductsTabVisible(final ViewDefinitionState view, final Entity productionTracking, final Entity order) {
        view.getComponentByReference(L_PRODUCTS_TAB)
                .setVisible(checkIfShouldProductTabBeVisible(productionTracking, order));
    }

    public boolean checkIfShouldProductTabBeVisible(final Entity productionTracking, final Entity order) {
        if (productionTracking == null) {
            return false;
        }

        if (order == null) {
            return false;
        }

        boolean registerQuantityInProduct = order.getBooleanField(OrderFieldsPC.REGISTER_QUANTITY_IN_PRODUCT);
        boolean registerQuantityOutProduct = order.getBooleanField(OrderFieldsPC.REGISTER_QUANTITY_OUT_PRODUCT);

        return (registerQuantityInProduct || registerQuantityOutProduct);
    }

    private void showLastStateChangeFailNotification(final FormComponent productionTrackingForm,
            final Entity productionTracking) {
        boolean lastStateChangeFails = productionTracking.getBooleanField(ProductionTrackingFields.LAST_STATE_CHANGE_FAILS);

        if (lastStateChangeFails) {
            String lastStateChangeFailCause = productionTracking
                    .getStringField(ProductionTrackingFields.LAST_STATE_CHANGE_FAIL_CAUSE);

            if (StringUtils.isEmpty(lastStateChangeFailCause)) {
                productionTrackingForm.addMessage("productionCounting.productionTracking.info.lastStateChangeFails",
                        ComponentState.MessageType.INFO, true, lastStateChangeFailCause);
            } else {
                productionTrackingForm.addMessage("productionCounting.productionTracking.info.lastStateChangeFails.withCause",
                        ComponentState.MessageType.INFO, false, lastStateChangeFailCause);
            }
        }
    }

    public void changeFieldComponentsEnabledAndGridsEditable(final ViewDefinitionState view) {
        FormComponent productionTrackingForm = (FormComponent) view.getComponentByReference(L_FORM);

        if (productionTrackingForm.getEntityId() == null) {
            return;
        }

        Entity productionTracking = productionTrackingForm.getEntity();

        String state = productionTracking.getStringField(ProductionTrackingFields.STATE);

        boolean isDraft = (ProductionTrackingStateStringValues.DRAFT.equals(state));
        boolean isExternalSynchronized = productionTracking.getBooleanField(ProductionTrackingFields.IS_EXTERNAL_SYNCHRONIZED);

        setFieldComponentsEnabledAndGridsEditable(view, isDraft && isExternalSynchronized);

        productionTrackingDetailsListeners.checkJustOne(view, null, null);
    }

    private void setFieldComponentsEnabledAndGridsEditable(final ViewDefinitionState view, final boolean isEnabled) {
        productionCountingService.setComponentsState(view, L_PRODUCTION_TRACKING_FIELD_NAMES, isEnabled, true);

        GridComponent trackingOperationProductInComponentsGrid = (GridComponent) view
                .getComponentByReference(ProductionTrackingFields.TRACKING_OPERATION_PRODUCT_IN_COMPONENTS);
        GridComponent trackingOperationProductOutComponentsGrid = (GridComponent) view
                .getComponentByReference(ProductionTrackingFields.TRACKING_OPERATION_PRODUCT_OUT_COMPONENTS);

        GridComponent stateChangesGrid = (GridComponent) view.getComponentByReference(ProductionTrackingFields.STATE_CHANGES);

        trackingOperationProductInComponentsGrid.setEnabled(isEnabled);
        trackingOperationProductOutComponentsGrid.setEnabled(isEnabled);

        stateChangesGrid.setEditable(isEnabled);
    }

    public void updateRibbonState(final ViewDefinitionState view) {
        FormComponent productionTrackingForm = (FormComponent) view.getComponentByReference(L_FORM);

        WindowComponent window = (WindowComponent) view.getComponentByReference(L_WINDOW);

        RibbonGroup actionsRibbonGroup = window.getRibbon().getGroupByName(L_ACTIONS);
        RibbonGroup productsQuantitiesRibbonGroup = window.getRibbon().getGroupByName(L_PRODUCTS_QUANTITIES);
        RibbonGroup productionCountingQuantitiesRibbonGroup = window.getRibbon().getGroupByName(L_PRODUCTION_COUNTING_QUANTITIES);
        RibbonGroup anomaliesRibbonGroup = window.getRibbon().getGroupByName(L_ANOMALIES);

        RibbonActionItem copyRibbonActionItem = actionsRibbonGroup.getItemByName(L_COPY);
        RibbonActionItem copyPlannedQuantityToUsedQuantityRibbonActionItem = productsQuantitiesRibbonGroup
                .getItemByName(L_COPY_PLANNED_QUANTITY_TO_USED_QUANTITY);
        RibbonActionItem productionCountingQuantitiesRibbonActionItem = productionCountingQuantitiesRibbonGroup
                .getItemByName(L_PRODUCTION_COUNTING_QUANTITIES);
        RibbonActionItem addToAnomaliesListRibbonActionItem = anomaliesRibbonGroup.getItemByName(L_ADD_TO_ANOMALIES_LIST);

        if (productionTrackingForm.getEntityId() == null) {
            return;
        }

        Entity productionTracking = productionTrackingForm.getEntity();
        Entity order = productionTracking.getBelongsToField(ProductionTrackingFields.ORDER);

        if (order == null) {
            return;
        }

        String state = productionTracking.getStringField(ProductionTrackingFields.STATE);
        String orderState = order.getStringField(OrderFields.STATE);

        boolean isInProgress = OrderStateStringValues.IN_PROGRESS.equals(orderState);
        boolean isDraft = ProductionTrackingStateStringValues.DRAFT.equals(state);
        boolean registerQuantityInProduct = order.getBooleanField(OrderFieldsPC.REGISTER_QUANTITY_IN_PRODUCT);
        boolean registerQuantityOutProduct = order.getBooleanField(OrderFieldsPC.REGISTER_QUANTITY_OUT_PRODUCT);

        copyRibbonActionItem.setEnabled(isInProgress);
        copyPlannedQuantityToUsedQuantityRibbonActionItem
                .setEnabled(isDraft && (registerQuantityInProduct || registerQuantityOutProduct));
        productionCountingQuantitiesRibbonActionItem
                .setEnabled(isDraft && (registerQuantityInProduct || registerQuantityOutProduct));
        addToAnomaliesListRibbonActionItem.setEnabled(isDraft && (registerQuantityInProduct || registerQuantityOutProduct));

        copyRibbonActionItem.requestUpdate(true);
        copyPlannedQuantityToUsedQuantityRibbonActionItem.requestUpdate(true);
        productionCountingQuantitiesRibbonActionItem.requestUpdate(true);
        addToAnomaliesListRibbonActionItem.requestUpdate(true);
    }

}
