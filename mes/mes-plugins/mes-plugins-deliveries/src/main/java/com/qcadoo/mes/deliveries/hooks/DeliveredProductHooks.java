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
package com.qcadoo.mes.deliveries.hooks;

import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.ReservationService;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveriesConstants;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.mes.deliveries.constants.ParameterFieldsD;
import com.qcadoo.mes.materialFlowResources.PalletValidatorService;
import com.qcadoo.mes.materialFlowResources.constants.MaterialFlowResourcesConstants;
import com.qcadoo.mes.materialFlowResources.constants.StorageLocationFields;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.plugin.api.PluginUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.qcadoo.mes.deliveries.constants.DeliveredProductFields.ADDITIONAL_CODE;
import static com.qcadoo.mes.deliveries.constants.DeliveredProductFields.DAMAGED_QUANTITY;
import static com.qcadoo.mes.deliveries.constants.DeliveredProductFields.DELIVERED_QUANTITY;
import static com.qcadoo.mes.deliveries.constants.DeliveredProductFields.DELIVERY;
import static com.qcadoo.mes.deliveries.constants.DeliveredProductFields.PALLET_NUMBER;
import static com.qcadoo.mes.deliveries.constants.DeliveredProductFields.PRODUCT;

@Service
public class DeliveredProductHooks {

    public static final String OFFER = "offer";

    public static final String OPERATION = "operation";

    public static final String EXPIRATION_DATE = "expirationDate";

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public void onCreate(final DataDefinition deliveredProductDD, final Entity deliveredProduct) {
        reservationService.createDefaultReservationsForDeliveredProduct(deliveredProduct);
    }

    public void onSave(final DataDefinition deliveredProductDD, final Entity deliveredProduct) {
        reservationService.deleteReservationsForDeliveredProductIfChanged(deliveredProduct);
        updateDeliveredQuantityInOrderedProduct(deliveredProduct);
        tryFillStorageLocation(deliveredProduct);
    }

    private void tryFillStorageLocation(Entity deliveredProduct) {
        Entity delivery = deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY);
        Entity location = delivery.getBelongsToField(DeliveryFields.LOCATION);
        if (Objects.nonNull(location)
                && Objects.isNull(deliveredProduct.getBelongsToField(DeliveredProductFields.STORAGE_LOCATION))) {
            Entity product = deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT);
            Optional<Entity> storageLocation = findStorageLocationForProduct(product, location);
            if (storageLocation.isPresent()) {
                deliveredProduct.setField(DeliveredProductFields.STORAGE_LOCATION, storageLocation.get());
            }
        }
    }

    public boolean onDelete(final DataDefinition dataDefinition, final Entity deliveredProduct) {
        SearchCriteriaBuilder searchCriteriaBuilder = dataDefinitionService
                .get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_ORDERED_PRODUCT)
                .find()
                .add(SearchRestrictions.belongsTo(OrderedProductFields.DELIVERY,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.PRODUCT,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.ADDITIONAL_CODE,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE)));

        if (PluginUtils.isEnabled("techSubcontrForDeliveries")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OPERATION, deliveredProduct.getBelongsToField(OPERATION)));
        }

        if (PluginUtils.isEnabled("supplyNegotiations")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OFFER, deliveredProduct.getBelongsToField(OFFER)));
        }
        Optional<Entity> maybeOrderedProduct = Optional.ofNullable(searchCriteriaBuilder.setMaxResults(1).uniqueResult());
        maybeOrderedProduct.ifPresent(orderedProduct -> {
            BigDecimal deliveredQuantity = BigDecimal.ZERO;
            BigDecimal additionalQuantity = BigDecimal.ZERO;

            List<Entity> dProducts = getCriteriaForDeliveredProductByGroup(deliveredProduct).list().getEntities();
            if (!dProducts.isEmpty()) {
                BigDecimal deliveredQuantityRest = dProducts.stream()
                        .map(dp -> dp.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                deliveredQuantity = deliveredQuantity.add(deliveredQuantityRest, numberService.getMathContext());
                BigDecimal additionalQuantityRest = dProducts.stream()
                        .map(dp -> dp.getDecimalField(DeliveredProductFields.ADDITIONAL_QUANTITY))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                additionalQuantity = additionalQuantity.add(additionalQuantityRest, numberService.getMathContext());
            }
            orderedProduct.setField(OrderedProductFields.DELIVERED_QUANTITY, numberService.setScaleWithDefaultMathContext(deliveredQuantity));
            orderedProduct.setField(OrderedProductFields.ADDITIONAL_DELIVERED_QUANTITY,
                    numberService.setScaleWithDefaultMathContext(additionalQuantity));
            orderedProduct = orderedProduct.getDataDefinition().save(orderedProduct);

        });
        return true;
    }

    private void updateDeliveredQuantityInOrderedProduct(final Entity deliveredProduct) {

        if (Objects.nonNull(deliveredProduct.getId())) {
            Entity deliveredProductDB = deliveredProduct.getDataDefinition().get(deliveredProduct.getId());
            boolean isDeliveredProductChange = checkIfDeliveredProductChange(deliveredProductDB, deliveredProduct);
            if (isDeliveredProductChange) {
                SearchCriteriaBuilder scb = getCriteriaForOrderedProduct(deliveredProductDB);

                Optional<Entity> maybeOrderedProduct = Optional.ofNullable(scb.setMaxResults(1).uniqueResult());
                maybeOrderedProduct.ifPresent(orderedProduct -> {
                    BigDecimal deliveredQuantity = BigDecimal.ZERO;
                    BigDecimal additionalQuantity = BigDecimal.ZERO;

                    List<Entity> dProducts = getCriteriaForDeliveredProductByGroup(deliveredProductDB).list().getEntities();
                    if (!dProducts.isEmpty()) {
                        BigDecimal deliveredQuantityRest = dProducts.stream()
                                .map(dp -> dp.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        deliveredQuantity = deliveredQuantity.add(deliveredQuantityRest, numberService.getMathContext());
                        BigDecimal additionalQuantityRest = dProducts.stream()
                                .map(dp -> dp.getDecimalField(DeliveredProductFields.ADDITIONAL_QUANTITY))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        additionalQuantity = additionalQuantity.add(additionalQuantityRest, numberService.getMathContext());
                    }

                    orderedProduct.setField(OrderedProductFields.DELIVERED_QUANTITY, numberService.setScaleWithDefaultMathContext(deliveredQuantity));
                    orderedProduct.setField(OrderedProductFields.ADDITIONAL_DELIVERED_QUANTITY,
                            numberService.setScaleWithDefaultMathContext(additionalQuantity));
                    orderedProduct = orderedProduct.getDataDefinition().save(orderedProduct);
                });
            }
        }
        SearchCriteriaBuilder searchCriteriaBuilder = getCriteriaForOrderedProduct(deliveredProduct);

        Optional<Entity> maybeOrderedProduct = Optional.ofNullable(searchCriteriaBuilder.setMaxResults(1).uniqueResult());
        maybeOrderedProduct.ifPresent(orderedProduct -> {
            BigDecimal deliveredQuantity = BigDecimalUtils.convertNullToZero(deliveredProduct
                    .getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY));
            BigDecimal additionalQuantity = BigDecimalUtils.convertNullToZero(deliveredProduct
                    .getDecimalField(DeliveredProductFields.ADDITIONAL_QUANTITY));

            List<Entity> dProducts = getCriteriaForDeliveredProductByGroup(deliveredProduct).list().getEntities();
            if (!dProducts.isEmpty()) {
                BigDecimal deliveredQuantityRest = dProducts.stream()
                        .map(dp -> dp.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                deliveredQuantity = deliveredQuantity.add(deliveredQuantityRest, numberService.getMathContext());
                BigDecimal additionalQuantityRest = dProducts.stream()
                        .map(dp -> dp.getDecimalField(DeliveredProductFields.ADDITIONAL_QUANTITY))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                additionalQuantity = additionalQuantity.add(additionalQuantityRest, numberService.getMathContext());
            }

            orderedProduct.setField(OrderedProductFields.DELIVERED_QUANTITY, numberService.setScaleWithDefaultMathContext(deliveredQuantity));
            orderedProduct.setField(OrderedProductFields.ADDITIONAL_DELIVERED_QUANTITY,
                    numberService.setScaleWithDefaultMathContext(additionalQuantity));
            orderedProduct = orderedProduct.getDataDefinition().save(orderedProduct);
        });
    }

    private boolean checkIfDeliveredProductChange(final Entity deliveredProductDB, final Entity deliveredProduct) {
        if (!deliveredProductDB.getBelongsToField(DeliveredProductFields.PRODUCT).getId()
                .equals(deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT).getId())) {
            return true;
        }
        if (Objects.isNull(deliveredProductDB.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE)) != Objects
                .isNull(deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE))
                || (Objects.nonNull(deliveredProductDB.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE))
                        && Objects.nonNull(deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE)) && !deliveredProductDB
                        .getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE).getId()
                        .equals(deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE).getId()))) {
            return true;
        }
        if (Objects.isNull(deliveredProductDB.getBelongsToField(OFFER)) != Objects.isNull(deliveredProduct
                .getBelongsToField(OFFER))
                || (Objects.nonNull(deliveredProductDB.getBelongsToField(OFFER))
                        && Objects.nonNull(deliveredProduct.getBelongsToField(OFFER)) && !deliveredProductDB
                        .getBelongsToField(OFFER).getId().equals(deliveredProduct.getBelongsToField(OFFER).getId()))) {
            return true;
        }
        return false;
    }

    private SearchCriteriaBuilder getCriteriaForOrderedProduct(final Entity deliveredProduct) {
        SearchCriteriaBuilder searchCriteriaBuilder = dataDefinitionService
                .get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_ORDERED_PRODUCT)
                .find()
                .add(SearchRestrictions.belongsTo(OrderedProductFields.DELIVERY,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.PRODUCT,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.ADDITIONAL_CODE,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE)));

        if (PluginUtils.isEnabled("techSubcontrForDeliveries")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OPERATION, deliveredProduct.getBelongsToField(OPERATION)));
        }

        if (PluginUtils.isEnabled("supplyNegotiations")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OFFER, deliveredProduct.getBelongsToField(OFFER)));
        }
        return searchCriteriaBuilder;
    }

    private SearchCriteriaBuilder getCriteriaForDeliveredProductByGroup(final Entity deliveredProduct) {
        SearchCriteriaBuilder searchCriteriaBuilder = dataDefinitionService
                .get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_DELIVERED_PRODUCT)
                .find()
                .add(SearchRestrictions.belongsTo(DeliveredProductFields.DELIVERY,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.PRODUCT,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.ADDITIONAL_CODE,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE)));

        if (PluginUtils.isEnabled("techSubcontrForDeliveries")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OPERATION, deliveredProduct.getBelongsToField(OPERATION)));
        }

        if (PluginUtils.isEnabled("supplyNegotiations")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OFFER, deliveredProduct.getBelongsToField(OFFER)));
        }
        if (deliveredProduct.getId() != null) {
            searchCriteriaBuilder.add(SearchRestrictions.ne("id", deliveredProduct.getId()));
        }
        return searchCriteriaBuilder;
    }

    public void calculateDeliveredProductPricePerUnit(final DataDefinition deliveredProductDD, final Entity deliveredProduct) {
        deliveriesService.calculatePricePerUnit(deliveredProduct, DeliveredProductFields.DELIVERED_QUANTITY);
    }

    public boolean validatesWith(final DataDefinition deliveredProductDD, final Entity deliveredProduct) {
        return checkIfDeliveredProductAlreadyExists(deliveredProductDD, deliveredProduct)
                && checkIfDeliveredQuantityIsLessThanDamagedQuantity(deliveredProductDD, deliveredProduct)
                && checkIfDeliveredQuantityIsLessThanOrderedQuantity(deliveredProductDD, deliveredProduct)
                && validatePallet(deliveredProductDD, deliveredProduct)
                && notTooManyPalletsInStorageLocation(deliveredProductDD, deliveredProduct);
    }

    public boolean checkIfDeliveredProductAlreadyExists(final DataDefinition deliveredProductDD, final Entity deliveredProduct) {
        SearchCriteriaBuilder searchCriteriaBuilder = getSearchRestrictions(deliveredProductDD.find(), deliveredProduct);

        if (deliveredProduct.getId() != null) {
            searchCriteriaBuilder.add(SearchRestrictions.ne("id", deliveredProduct.getId()));
        }

        Entity deliveredProductFromDB = searchCriteriaBuilder.setMaxResults(1).uniqueResult();

        if (deliveredProductFromDB == null) {
            return true;
        } else {
            deliveredProduct.addError(deliveredProductDD.getField(PRODUCT),
                    "deliveries.deliveredProduct.error.productAlreadyExists");

            return false;
        }
    }

    private SearchCriteriaBuilder getSearchRestrictions(final SearchCriteriaBuilder scb, final Entity deliveredProduct) {
        scb.add(SearchRestrictions.belongsTo(DELIVERY, deliveredProduct.getBelongsToField(DELIVERY))).add(
                SearchRestrictions.belongsTo(PRODUCT, deliveredProduct.getBelongsToField(PRODUCT)));

        if (PluginUtils.isEnabled("deliveriesToMaterialFlow")) {
            scb.add(SearchRestrictions.belongsTo(PALLET_NUMBER, deliveredProduct.getBelongsToField(PALLET_NUMBER))).add(
                    SearchRestrictions.belongsTo(ADDITIONAL_CODE, deliveredProduct.getBelongsToField(ADDITIONAL_CODE)));
            if (deliveredProduct.getField(EXPIRATION_DATE) != null) {
                scb.add(SearchRestrictions.eq(EXPIRATION_DATE, deliveredProduct.getField(EXPIRATION_DATE)));
            } else {
                scb.add(SearchRestrictions.isNull(EXPIRATION_DATE));
            }
        }

        if (PluginUtils.isEnabled("supplyNegotiations")) {
            scb.add(SearchRestrictions.belongsTo(OFFER, deliveredProduct.getBelongsToField(OFFER)));
        }
        return scb;
    }

    public boolean checkIfDeliveredQuantityIsLessThanDamagedQuantity(final DataDefinition deliveredProductDD,
            final Entity deliveredProduct) {
        BigDecimal damagedQuantity = deliveredProduct.getDecimalField(DAMAGED_QUANTITY);
        BigDecimal deliveredQuantity = deliveredProduct.getDecimalField(DELIVERED_QUANTITY);

        if ((damagedQuantity != null) && (deliveredQuantity != null) && (damagedQuantity.compareTo(deliveredQuantity) == 1)) {
            deliveredProduct.addError(deliveredProductDD.getField(DAMAGED_QUANTITY),
                    "deliveries.deliveredProduct.error.damagedQuantity.deliveredQuantityIsTooMuch");
            deliveredProduct.addError(deliveredProductDD.getField(DELIVERED_QUANTITY),
                    "deliveries.deliveredProduct.error.damagedQuantity.deliveredQuantityIsTooMuch");

            return false;
        }

        return true;
    }

    private boolean checkIfDeliveredQuantityIsLessThanOrderedQuantity(final DataDefinition deliveredProductDD,
            final Entity deliveredProduct) {
        if (isBiggerDeliveredQuantityAllowed()) {
            return true;
        }

        BigDecimal deliveredQuantity = BigDecimalUtils.convertNullToZero(deliveredProduct
                .getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY));

        SearchCriteriaBuilder searchCriteriaBuilder = getCriteriaForOrderedProduct(deliveredProduct);

        Optional<Entity> maybeOrderedProduct = Optional.ofNullable(searchCriteriaBuilder.setMaxResults(1).uniqueResult());

        if (maybeOrderedProduct.isPresent()) {
            List<Entity> dProducts = getCriteriaForDeliveredProductByGroup(deliveredProduct).list().getEntities();
            if (!dProducts.isEmpty()) {
                BigDecimal deliveredQuantityRest = dProducts.stream()
                        .map(dp -> dp.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                deliveredQuantity = deliveredQuantity.add(deliveredQuantityRest, numberService.getMathContext());

            }
        }

        Optional<Entity> orderedProduct = getOrderedProductForDeliveredProduct(deliveredProduct);
        BigDecimal orderedQuantity = orderedProduct.isPresent() ? orderedProduct.get().getDecimalField(
                OrderedProductFields.ORDERED_QUANTITY) : BigDecimal.ZERO;
        if (deliveredQuantity != null && deliveredQuantity.compareTo(orderedQuantity) > 0) {
            deliveredProduct.addError(deliveredProductDD.getField(DeliveredProductFields.DELIVERED_QUANTITY),
                    "deliveries.deliveredProduct.error.deliveredQuantity.biggerThanOrderedQuantity");
            return false;
        }
        return true;
    }

    private Optional<Entity> getOrderedProductForDeliveredProduct(final Entity deliveredProduct) {
        DataDefinition orderedProductDD = dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER,
                DeliveriesConstants.MODEL_ORDERED_PRODUCT);
        SearchCriteriaBuilder searchCriteriaBuilder = orderedProductDD
                .find()
                .add(SearchRestrictions.belongsTo(OrderedProductFields.DELIVERY,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.PRODUCT,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT)))
                .add(SearchRestrictions.belongsTo(OrderedProductFields.ADDITIONAL_CODE,
                        deliveredProduct.getBelongsToField(DeliveredProductFields.ADDITIONAL_CODE)));
        if (PluginUtils.isEnabled("supplyNegotiations")) {
            searchCriteriaBuilder.add(SearchRestrictions.belongsTo(OFFER, deliveredProduct.getBelongsToField(OFFER)));
        }
        Entity orderedProduct = searchCriteriaBuilder.setMaxResults(1).uniqueResult();
        return Optional.ofNullable(orderedProduct);

    }

    private boolean isBiggerDeliveredQuantityAllowed() {
        return parameterService.getParameter().getBooleanField(ParameterFieldsD.DELIVERED_BIGGER_THAN_ORDERED);
    }

    @Autowired
    private PalletValidatorService palletValidatorService;

    private boolean validatePallet(final DataDefinition deliveredProductDD, final Entity deliveredProduct) {
        if ((deliveredProduct.getField(DeliveredProductFields.VALIDATE_PALLET) == null)
                || deliveredProduct.getBooleanField(DeliveredProductFields.VALIDATE_PALLET)) {
            return palletValidatorService.validatePalletForDeliveredProduct(deliveredProduct);
        }
        return true;
    }

    private boolean notTooManyPalletsInStorageLocation(DataDefinition deliveredProductDD, Entity deliveredProduct) {
        Entity storageLocation = deliveredProduct.getBelongsToField(DeliveredProductFields.STORAGE_LOCATION);
        final BigDecimal maxNumberOfPallets;
        if (storageLocation != null
                && (maxNumberOfPallets = storageLocation.getDecimalField(StorageLocationFields.MAXIMUM_NUMBER_OF_PALLETS)) != null) {

            Entity palletNumber = deliveredProduct.getBelongsToField(DeliveredProductFields.PALLET_NUMBER);
            if (palletNumber != null) {

                String query = "SELECT count(DISTINCT palletsInStorageLocation.palletnumber_id) AS palletsCount     "
                        + "   FROM (SELECT                                                                          "
                        + "           resource.palletnumber_id,                                                     "
                        + "           resource.storagelocation_id                                                   "
                        + "         FROM materialflowresources_resource resource                                    "
                        + "         UNION ALL SELECT                                                                "
                        + "                     deliveredproduct.palletnumber_id,                                   "
                        + "                     deliveredproduct.storagelocation_id                                 "
                        + "                   FROM deliveries_delivery delivery                                     "
                        + "                     JOIN deliveries_deliveredproduct deliveredproduct                   "
                        + "                       ON deliveredproduct.delivery_id = delivery.id                     "
                        + "                   WHERE                                                                 "
                        + "                     delivery.state not in ('06received','04declined') AND                                      "
                        + "                     deliveredproduct.id <> :deliveredProductId                          "
                        + "        ) palletsInStorageLocation                                                       "
                        + "   WHERE palletsInStorageLocation.storagelocation_id = :storageLocationId AND            "
                        + "         palletsInStorageLocation.palletnumber_id <> :palletNumberId";

                Long deliveredProductId = Optional.ofNullable(deliveredProduct.getId()).orElse(-1L);
                Long palletsCount = jdbcTemplate.queryForObject(
                        query,
                        new MapSqlParameterSource().addValue("storageLocationId", storageLocation.getId())
                                .addValue("palletNumberId", palletNumber.getId())
                                .addValue("deliveredProductId", deliveredProductId), Long.class);

                boolean valid = maxNumberOfPallets.compareTo(BigDecimal.valueOf(palletsCount)) > 0;
                if (!valid) {
                    deliveredProduct.addError(deliveredProductDD.getField(DeliveredProductFields.STORAGE_LOCATION),
                            "deliveries.deliveredProduct.error.storageLocationPalletLimitExceeded");
                }
                return valid;
            }
        }
        return true;
    }

    public Optional<Entity> findStorageLocationForProduct(final Entity product, final Entity location) {
        SearchCriteriaBuilder scb = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_STORAGE_LOCATION).find();
        scb.add(SearchRestrictions.belongsTo(StorageLocationFields.PRODUCT, product));
        scb.add(SearchRestrictions.belongsTo(StorageLocationFields.LOCATION, location));
        return Optional.ofNullable(scb.setMaxResults(1).uniqueResult());
    }

}
