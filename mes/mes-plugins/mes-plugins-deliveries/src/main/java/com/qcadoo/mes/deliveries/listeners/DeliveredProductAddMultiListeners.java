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
package com.qcadoo.mes.deliveries.listeners;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.qcadoo.mes.basic.CalculationQuantityService;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.constants.UnitConversionItemFieldsB;
import com.qcadoo.mes.basic.util.ProductUnitsConversionService;
import com.qcadoo.mes.deliveries.DeliveredProductMultiPositionService;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveredProductMultiFields;
import com.qcadoo.mes.deliveries.constants.DeliveredProductMultiPositionFields;
import com.qcadoo.mes.deliveries.constants.DeliveriesConstants;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.helpers.DeliveredMultiProduct;
import com.qcadoo.mes.deliveries.helpers.DeliveredMultiProductContainer;
import com.qcadoo.mes.deliveries.hooks.DeliveredProductAddMultiHooks;
import com.qcadoo.mes.materialFlowResources.constants.LocationFieldsMFR;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.units.PossibleUnitConversions;
import com.qcadoo.model.api.units.UnitConversionService;
import com.qcadoo.model.api.validators.ErrorMessage;
import com.qcadoo.plugin.api.PluginUtils;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.AwesomeDynamicListComponent;
import com.qcadoo.view.api.components.CheckBoxComponent;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;

@Component
public class DeliveredProductAddMultiListeners {

    private static final String L_FORM = "form";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private UnitConversionService unitConversionService;

    @Autowired
    private DeliveredProductAddMultiHooks deliveredProductAddMultiHooks;

    @Autowired
    private DeliveredProductMultiPositionService deliveredProductMultiPositionService;

    @Autowired
    private CalculationQuantityService calculationQuantityService;

    public void createDeliveredProducts(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent deliveredProductMultiForm = (FormComponent) view.getComponentByReference(L_FORM);

        Entity deliveredProductMulti = deliveredProductMultiForm.getPersistedEntityWithIncludedFormValues();

        CheckBoxComponent generated = (CheckBoxComponent) view.getComponentByReference("generated");
        try {

            if (!validate(deliveredProductMulti)) {
                deliveredProductMultiForm.setEntity(deliveredProductMulti);
                view.addMessage("deliveries.deliveredProductMulti.error.invalid", MessageType.FAILURE);
                generated.setChecked(false);
                return;
            }

            List<Entity> deliveredProductMultiPositions = deliveredProductMulti
                    .getHasManyField(DeliveredProductMultiFields.DELIVERED_PRODUCT_MULTI_POSITIONS);

            if (deliveredProductMultiPositions.isEmpty()) {
                view.addMessage("deliveries.deliveredProductMulti.error.emptyPositions", MessageType.FAILURE);
                generated.setChecked(false);
                return;
            }

            trySaveDeliveredProducts(deliveredProductMulti, deliveredProductMultiPositions);
            deliveredProductMultiForm.setEntity(deliveredProductMulti);

            if (deliveredProductMulti.isValid()) {
                state.performEvent(view, "save");

                view.addMessage("deliveries.deliveredProductMulti.success", MessageType.SUCCESS);
                generated.setChecked(true);
            }
        } catch (Exception ex) {
            generated.setChecked(false);
            deliveredProductMultiForm.setEntity(deliveredProductMulti);
        }

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySaveDeliveredProducts(final Entity deliveredProductMulti, final List<Entity> deliveredProductMultiPositions) {
        Entity delivery = deliveredProductMulti.getBelongsToField(DeliveredProductMultiFields.DELIVERY);

        for (Entity position : deliveredProductMultiPositions) {
            Entity deliveredProduct = createDeliveredProduct(position, getDeliveredProductDD());

            setStorageLocationFields(deliveredProduct, deliveredProductMulti);

            deliveredProduct.setField(DeliveredProductFields.DELIVERY, delivery);
            deliveredProduct = deliveredProduct.getDataDefinition().save(deliveredProduct);

            if (!deliveredProduct.isValid()) {
                for (Map.Entry<String, ErrorMessage> entry : deliveredProduct.getErrors().entrySet()) {
                    if (position.getDataDefinition().getField(entry.getKey()) != null) {
                        position.addError(position.getDataDefinition().getField(entry.getKey()), entry.getValue().getMessage());
                    } else {
                        position.addGlobalError(entry.getValue().getMessage(), false);
                    }
                }

                deliveredProductMulti.addGlobalError("deliveries.deliveredProductMulti.error.invalid");

                throw new IllegalStateException("Undone saved delivered product");
            }
        }
    }

    private void setStorageLocationFields(Entity deliveredProduct, Entity deliveredProductMulti) {
        deliveredProduct.setField(DeliveredProductFields.PALLET_NUMBER,
                deliveredProductMulti.getBelongsToField(DeliveredProductMultiFields.PALLET_NUMBER));
        deliveredProduct.setField(DeliveredProductFields.PALLET_TYPE,
                deliveredProductMulti.getField(DeliveredProductMultiFields.PALLET_TYPE));
        deliveredProduct.setField(DeliveredProductFields.STORAGE_LOCATION,
                deliveredProductMulti.getBelongsToField(DeliveredProductMultiFields.STORAGE_LOCATION));
    }

    private boolean validate(Entity deliveredProductMulti) {
        if (!deliveryHasLocationSet(deliveredProductMulti)) {
            return false;
        }

        DataDefinition deliveredProductMultiDD = deliveredProductMulti.getDataDefinition();

        boolean isValid = true;

        Arrays.asList(DeliveredProductMultiFields.PALLET_NUMBER, DeliveredProductMultiFields.PALLET_TYPE,
                DeliveredProductMultiFields.STORAGE_LOCATION)
                .stream()
                .forEach(
                        fieldName -> {
                            if (deliveredProductMulti.getField(fieldName) == null) {
                                deliveredProductMulti.addError(deliveredProductMultiDD.getField(fieldName),
                                        "qcadooView.validate.field.error.missing");
                            }
                        });

        isValid = deliveredProductMulti.isValid();

        DataDefinition deliveredProductMultiPositionDD = getDeliveredProductMultiPositionDD();

        List<Entity> deliveredProductMultiPositions = deliveredProductMulti
                .getHasManyField(DeliveredProductMultiFields.DELIVERED_PRODUCT_MULTI_POSITIONS);

        Multimap<Long, Date> positionsMap = ArrayListMultimap.create();

        DeliveredMultiProductContainer multiProductContainer = new DeliveredMultiProductContainer();

        for (Entity position : deliveredProductMultiPositions) {
            checkExpirationDate(deliveredProductMulti, position, DeliveredProductMultiPositionFields.EXPIRATION_DATE,
                    deliveredProductMultiPositionDD);
            checkMissing(position, DeliveredProductMultiPositionFields.PRODUCT, deliveredProductMultiPositionDD);
            checkMissingOrZero(position, DeliveredProductMultiPositionFields.QUANTITY, deliveredProductMultiPositionDD);
            checkMissingOrZero(position, DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY, deliveredProductMultiPositionDD);
            checkMissingOrZero(position, DeliveredProductMultiPositionFields.CONVERSION, deliveredProductMultiPositionDD);

            if (position.isValid()) {
                Entity product = position.getBelongsToField(DeliveredProductMultiPositionFields.PRODUCT);
                Entity additionalCode = position.getBelongsToField(DeliveredProductMultiPositionFields.ADDITIONAL_CODE);
                Date expirationDate = position.getDateField(DeliveredProductMultiPositionFields.EXPIRATION_DATE);

                if (multiProductContainer.checkIfExsists(new DeliveredMultiProduct(mapToId(product), mapToId(additionalCode),
                        expirationDate))) {
                    position.addError(deliveredProductMultiPositionDD.getField(DeliveredProductMultiPositionFields.PRODUCT),
                            "deliveries.deliveredProductMulti.error.productExists");
                } else {
                    DeliveredMultiProduct deliveredMultiProduct = new DeliveredMultiProduct(mapToId(product),
                            mapToId(additionalCode), expirationDate);

                    multiProductContainer.addProduct(deliveredMultiProduct);
                }
            }

            isValid = isValid && position.isValid();
        }

        return isValid;
    }

    private boolean deliveryHasLocationSet(Entity deliveredProductMulti) {
        Entity delivery = deliveredProductMulti.getBelongsToField(DeliveredProductMultiFields.DELIVERY);
        Entity location = delivery.getBelongsToField(DeliveryFields.LOCATION);

        if (location == null) {
            deliveredProductMulti.addGlobalError("deliveries.deliveredProductMultiPosition.error.locationRequired");

            return false;
        }

        return true;
    }

    private Long mapToId(Entity entity) {
        if (entity == null) {
            return null;
        }

        return entity.getId();
    }

    private void checkMissing(Entity position, String fieldname, DataDefinition positionDataDefinition) {
        if (position.getField(fieldname) == null) {
            position.addError(positionDataDefinition.getField(fieldname), "qcadooView.validate.field.error.missing");
        }
    }

    private void checkMissingOrZero(Entity position, String fieldname, DataDefinition positionDataDefinition) {
        if (position.getField(fieldname) == null) {
            position.addError(positionDataDefinition.getField(fieldname), "qcadooView.validate.field.error.missing");
        } else if (BigDecimal.ZERO.compareTo(position.getDecimalField(fieldname)) >= 0) {
            position.addError(positionDataDefinition.getField(fieldname), "qcadooView.validate.field.error.outOfRange.toSmall");
        }
    }

    private void checkExpirationDate(Entity deliveredProductMulti, Entity position, String fieldname,
            DataDefinition positionDataDefinition) {
        Entity delivery = deliveredProductMulti.getBelongsToField(DeliveredProductMultiFields.DELIVERY);
        Entity location = delivery.getBelongsToField(DeliveryFields.LOCATION);

        if (location != null) {
            Date expirationDate = position.getDateField(DeliveredProductMultiPositionFields.EXPIRATION_DATE);

            boolean requireExpirationDate = location.getBooleanField(LocationFieldsMFR.REQUIRE_EXPIRATION_DATE);

            if (requireExpirationDate && expirationDate == null) {
                position.addError(positionDataDefinition.getField(fieldname), "qcadooView.validate.field.error.missing");
            }
        }
    }

    private Entity createDeliveredProduct(final Entity position, final DataDefinition deliveredProductDD) {
        Entity deliveredProduct = deliveredProductDD.create();

        deliveredProduct.setField(DeliveredProductFields.PRODUCT,
                position.getBelongsToField(DeliveredProductMultiPositionFields.PRODUCT));
        deliveredProduct.setField(DeliveredProductFields.DELIVERED_QUANTITY,
                position.getDecimalField(DeliveredProductMultiPositionFields.QUANTITY));
        deliveredProduct.setField(DeliveredProductFields.ADDITIONAL_QUANTITY,
                position.getDecimalField(DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY));
        deliveredProduct.setField(DeliveredProductFields.CONVERSION,
                position.getDecimalField(DeliveredProductMultiPositionFields.CONVERSION));
        deliveredProduct.setField(DeliveredProductFields.IS_WASTE,
                position.getBooleanField(DeliveredProductMultiPositionFields.IS_WASTE));
        deliveredProduct.setField(DeliveredProductFields.BATCH,
                position.getStringField(DeliveredProductMultiPositionFields.BATCH));
        deliveredProduct.setField(DeliveredProductFields.ADDITIONAL_UNIT,
                position.getStringField(DeliveredProductMultiPositionFields.ADDITIONAL_UNIT));
        deliveredProduct.setField(DeliveredProductFields.ADDITIONAL_CODE,
                position.getBelongsToField(DeliveredProductMultiPositionFields.ADDITIONAL_CODE));

        if (PluginUtils.isEnabled("supplyNegotiations")) {
            if (Objects.nonNull(position.getId())) {
                deliveredProduct.setField("offer", position.getDataDefinition().get(position.getId()).getBelongsToField("offer"));
            }
        }

        return deliveredProduct;
    }

    public void additionalCodeChanged(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        Entity delivery = extractDeliveryEntityFromView(view);

        AwesomeDynamicListComponent deliveredProductMultiPositions = (AwesomeDynamicListComponent) view
                .getComponentByReference(DeliveredProductMultiFields.DELIVERED_PRODUCT_MULTI_POSITIONS);

        List<FormComponent> deliveredProductMultiPositionsFormComponents = deliveredProductMultiPositions.getFormComponents();
        for (FormComponent deliveredProductMultiPositionsFormComponent : deliveredProductMultiPositionsFormComponents) {
            Entity deliveredProductMultiPosition = deliveredProductMultiPositionsFormComponent.getEntity();

            LookupComponent additionalCodeComponent = (LookupComponent) deliveredProductMultiPositionsFormComponent
                    .findFieldComponentByName(DeliveredProductMultiPositionFields.ADDITIONAL_CODE);

            if (additionalCodeComponent.getUuid().equals(state.getUuid())) {
                recalculateQuantities(delivery, deliveredProductMultiPosition);

                FieldComponent quantityComponent = deliveredProductMultiPositionsFormComponent
                        .findFieldComponentByName(DeliveredProductMultiPositionFields.QUANTITY);
                quantityComponent.setFieldValue(numberService.formatWithMinimumFractionDigits(
                        deliveredProductMultiPosition.getField(DeliveredProductMultiPositionFields.QUANTITY), 0));
                quantityComponent.requestComponentUpdateState();

                FieldComponent additionalQuantityComponent = deliveredProductMultiPositionsFormComponent
                        .findFieldComponentByName(DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY);
                additionalQuantityComponent.setFieldValue(numberService.formatWithMinimumFractionDigits(
                        deliveredProductMultiPosition.getField(DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY), 0));
                additionalQuantityComponent.requestComponentUpdateState();
            }

        }

    }

    public void productChanged(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        Entity delivery = extractDeliveryEntityFromView(view);

        AwesomeDynamicListComponent deliveredProductMultiPositions = (AwesomeDynamicListComponent) view
                .getComponentByReference(DeliveredProductMultiFields.DELIVERED_PRODUCT_MULTI_POSITIONS);

        List<FormComponent> deliveredProductMultiPositionsFormComponents = deliveredProductMultiPositions.getFormComponents();
        for (FormComponent deliveredProductMultiPositionsFormComponent : deliveredProductMultiPositionsFormComponents) {
            Entity deliveredProductMultiPosition = deliveredProductMultiPositionsFormComponent.getEntity();

            Entity product = deliveredProductMultiPosition.getBelongsToField(DeliveredProductMultiPositionFields.PRODUCT);
            LookupComponent additionalCodeComponent = (LookupComponent) deliveredProductMultiPositionsFormComponent
                    .findFieldComponentByName(DeliveredProductMultiPositionFields.ADDITIONAL_CODE);
            LookupComponent productComponent = (LookupComponent) deliveredProductMultiPositionsFormComponent
                    .findFieldComponentByName(DeliveredProductMultiPositionFields.PRODUCT);

            if (productComponent.getUuid().equals(state.getUuid())) {
                deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.ADDITIONAL_CODE, null);
                recalculateQuantities(delivery, deliveredProductMultiPosition);

                deliveredProductAddMultiHooks.boldRequired(deliveredProductMultiPositionsFormComponent);
                deliveredProductAddMultiHooks.filterAdditionalCode(product, additionalCodeComponent);

                if (product != null) {
                    String unit = product.getStringField(ProductFields.UNIT);
                    deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.UNIT, unit);
                    String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);

                    FieldComponent conversionField = deliveredProductMultiPositionsFormComponent
                            .findFieldComponentByName(DeliveredProductMultiPositionFields.CONVERSION);

                    if (additionalUnit != null) {
                        conversionField.setEnabled(true);
                        deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.ADDITIONAL_UNIT,
                                additionalUnit);

                        BigDecimal conversion = getConversion(product, unit, additionalUnit);
                        deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.CONVERSION, conversion);
                    } else {
                        conversionField.setEnabled(false);
                        deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.ADDITIONAL_UNIT, unit);
                        deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.CONVERSION, BigDecimal.ONE);
                    }

                    deliveredProductMultiPositionsFormComponent.setEntity(deliveredProductMultiPosition);
                }
            }
        }
    }

    private Entity extractDeliveryEntityFromView(final ViewDefinitionState view) {
        FormComponent deliveredProductMultiForm = (FormComponent) view.getComponentByReference("form");

        Entity deliveredProductMulti = deliveredProductMultiForm.getPersistedEntityWithIncludedFormValues();

        return deliveredProductMulti.getBelongsToField(DeliveredProductMultiFields.DELIVERY);
    }

    private void recalculateQuantities(Entity delivery, Entity deliveredProductMultiPosition) {
        Entity product = deliveredProductMultiPosition.getBelongsToField(DeliveredProductMultiPositionFields.PRODUCT);
        Entity additionalCode = deliveredProductMultiPosition
                .getBelongsToField(DeliveredProductMultiPositionFields.ADDITIONAL_CODE);
        BigDecimal conversion = BigDecimal.ONE;

        if (product != null) {
            String unit = product.getStringField(ProductFields.UNIT);
            String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);

            if (StringUtils.isNotEmpty(additionalUnit)) {
                conversion = deliveredProductMultiPosition.getDecimalField(DeliveredProductMultiPositionFields.CONVERSION);

                if (conversion == null) {
                    conversion = getConversion(product, unit, additionalUnit);
                }
            }

            BigDecimal orderedQuantity = deliveredProductMultiPositionService.findOrderedQuantity(delivery, product,
                    additionalCode);
            BigDecimal alreadyAssignedQuantity = deliveredProductMultiPositionService.countAlreadyAssignedQuantityForProduct(
                    product, additionalCode, deliveredProductMultiPosition.getBelongsToField("offer"),
                    delivery.getHasManyField(DeliveryFields.DELIVERED_PRODUCTS));

            BigDecimal quantity = orderedQuantity.subtract(alreadyAssignedQuantity, numberService.getMathContext());

            if (BigDecimal.ZERO.compareTo(quantity) == 1) {
                quantity = BigDecimal.ZERO;
            }

            BigDecimal newAdditionalQuantity = calculationQuantityService.calculateAdditionalQuantity(quantity, conversion,
                    Optional.ofNullable(additionalUnit).orElse(unit));

            deliveredProductMultiPosition.setField(DeliveredProductMultiPositionFields.QUANTITY, quantity);
            deliveredProductMultiPosition
                    .setField(DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY, newAdditionalQuantity);
        }
    }

    private BigDecimal getConversion(Entity product, String unit, String additionalUnit) {
        PossibleUnitConversions unitConversions = unitConversionService.getPossibleConversions(unit,
                searchCriteriaBuilder -> searchCriteriaBuilder.add(SearchRestrictions.belongsTo(
                        UnitConversionItemFieldsB.PRODUCT, product)));

        if (unitConversions.isDefinedFor(additionalUnit)) {
            return unitConversions.asUnitToConversionMap().get(additionalUnit);
        } else {
            return BigDecimal.ZERO;
        }
    }

    public void quantityChanged(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        AwesomeDynamicListComponent deliveredProductMultiPositions = (AwesomeDynamicListComponent) view
                .getComponentByReference(DeliveredProductMultiFields.DELIVERED_PRODUCT_MULTI_POSITIONS);

        List<FormComponent> deliveredProductMultiPositionsFormComponents = deliveredProductMultiPositions.getFormComponents();

        for (FormComponent deliveredProductMultiPositionsFormComponent : deliveredProductMultiPositionsFormComponents) {
            Entity deliveredProductMultiPosition = deliveredProductMultiPositionsFormComponent
                    .getPersistedEntityWithIncludedFormValues();

            boolean quantityComponentInForm = state.getUuid()
                    .equals(deliveredProductMultiPositionsFormComponent.findFieldComponentByName("quantity").getUuid());
            boolean conversionComponentInForm = state.getUuid()
                    .equals(deliveredProductMultiPositionsFormComponent.findFieldComponentByName("conversion").getUuid());

            if (quantityComponentInForm || conversionComponentInForm) {

                Entity product = deliveredProductMultiPosition.getBelongsToField(DeliveredProductMultiPositionFields.PRODUCT);
                BigDecimal quantity = deliveredProductMultiPosition.getDecimalField(DeliveredProductMultiPositionFields.QUANTITY);
                BigDecimal conversion = deliveredProductMultiPosition
                        .getDecimalField(DeliveredProductMultiPositionFields.CONVERSION);

                if (conversion != null && quantity != null && product != null) {
                    String additionalQuantityUnit = Optional.ofNullable(product.getStringField(ProductFields.ADDITIONAL_UNIT))
                            .orElse(product.getStringField(ProductFields.UNIT));
                    FieldComponent additionalQuantity = deliveredProductMultiPositionsFormComponent
                            .findFieldComponentByName(DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY);
                    BigDecimal newAdditionalQuantity = calculationQuantityService.calculateAdditionalQuantity(quantity,
                            conversion, additionalQuantityUnit);
                    additionalQuantity.setFieldValue(numberService.formatWithMinimumFractionDigits(newAdditionalQuantity, 0));
                    additionalQuantity.requestComponentUpdateState();
                }
                break;
            }
        }
    }

    public void additionalQuantityChanged(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        AwesomeDynamicListComponent deliveredProductMultiPositions = (AwesomeDynamicListComponent) view
                .getComponentByReference(DeliveredProductMultiFields.DELIVERED_PRODUCT_MULTI_POSITIONS);

        List<FormComponent> deliveredProductMultiPositionsFormComponents = deliveredProductMultiPositions.getFormComponents();

        for (FormComponent deliveredProductMultiPositionsFormComponent : deliveredProductMultiPositionsFormComponents) {
            Entity deliveredProductMultiPosition = deliveredProductMultiPositionsFormComponent
                    .getPersistedEntityWithIncludedFormValues();

            if (state.getUuid().equals(
                    deliveredProductMultiPositionsFormComponent.findFieldComponentByName("additionalQuantity").getUuid())) {

                Entity product = deliveredProductMultiPosition.getBelongsToField(DeliveredProductMultiPositionFields.PRODUCT);
                BigDecimal additionalQuantity = deliveredProductMultiPosition
                        .getDecimalField(DeliveredProductMultiPositionFields.ADDITIONAL_QUANTITY);
                BigDecimal conversion = deliveredProductMultiPosition
                        .getDecimalField(DeliveredProductMultiPositionFields.CONVERSION);

                if (conversion != null && additionalQuantity != null && product != null) {
                    String unit = product.getStringField(ProductFields.UNIT);
                    FieldComponent quantity = deliveredProductMultiPositionsFormComponent
                            .findFieldComponentByName(DeliveredProductMultiPositionFields.QUANTITY);

                    BigDecimal newQuantity = calculationQuantityService.calculateQuantity(additionalQuantity, conversion, unit);

                    quantity.setFieldValue(numberService.formatWithMinimumFractionDigits(newQuantity, 0));
                    quantity.requestComponentUpdateState();
                }
                break;
            }
        }
    }

    public void onAddRow(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        AwesomeDynamicListComponent adlState = (AwesomeDynamicListComponent) state;

        for (FormComponent formComponent : adlState.getFormComponents()) {
            deliveredProductAddMultiHooks.boldRequired(formComponent);
        }

        productChanged(view, state, args);
    }

    private DataDefinition getDeliveredProductDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_DELIVERED_PRODUCT);
    }

    private DataDefinition getDeliveredProductMultiPositionDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER,
                DeliveriesConstants.MODEL_DELIVERED_PRODUCT_MULTI_POSITION);
    }

}
