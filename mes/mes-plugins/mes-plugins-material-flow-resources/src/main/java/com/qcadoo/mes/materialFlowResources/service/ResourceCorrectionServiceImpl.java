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
package com.qcadoo.mes.materialFlowResources.service;

import java.math.BigDecimal;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Strings;
import com.qcadoo.mes.basic.constants.PalletNumberFields;
import com.qcadoo.mes.materialFlowResources.constants.MaterialFlowResourcesConstants;
import com.qcadoo.mes.materialFlowResources.constants.ResourceCorrectionFields;
import com.qcadoo.mes.materialFlowResources.constants.ResourceFields;
import com.qcadoo.mes.materialFlowResources.constants.StorageLocationFields;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.DictionaryService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.view.api.utils.NumberGeneratorService;

@Service
public class ResourceCorrectionServiceImpl implements ResourceCorrectionService {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberGeneratorService numberGeneratorService;

    @Autowired
    private DictionaryService dictionaryService;

    @Autowired
    private NumberService numberService;

    public long getPalletsCountInStorageLocation(final Entity newStorageLocation) {
        StringBuilder hql = new StringBuilder();
        hql.append("select count(distinct p.number) as palletsCount from #materialFlowResources_resource r ");
        hql.append("join r.palletNumber p ");
        hql.append("join r.storageLocation sl ");
        hql.append("where sl.id = '").append(newStorageLocation.getId()).append("'");

        Entity result = dataDefinitionService
                .get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER, MaterialFlowResourcesConstants.MODEL_RESOURCE)
                .find(hql.toString()).setMaxResults(1).uniqueResult();
        return result.getLongField("palletsCount");
    }

    public long getPalletsCountInStorageLocationWithoutPalletNumber(final Entity newStorageLocation, final Entity newPalletNumber) {
        StringBuilder hql = new StringBuilder();
        hql.append("select count(distinct p.number) as palletsCount from #materialFlowResources_resource r ");
        hql.append("join r.palletNumber p ");
        hql.append("join r.storageLocation sl ");
        hql.append("where sl.id = '").append(newStorageLocation.getId()).append("' ");
        hql.append("and p.number != '").append(newPalletNumber.getStringField(PalletNumberFields.NUMBER)).append("'");

        Entity result = dataDefinitionService
                .get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER, MaterialFlowResourcesConstants.MODEL_RESOURCE)
                .find(hql.toString()).setMaxResults(1).uniqueResult();
        return result.getLongField("palletsCount");
    }

    @Override
    @Transactional
    public boolean createCorrectionForResource(final Entity resource) {
        Entity oldResource = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_RESOURCE).get(resource.getId());
        BigDecimal newQuantity = resource.getDecimalField(ResourceFields.QUANTITY);
        BigDecimal newPrice = resource.getDecimalField(ResourceFields.PRICE);
        BigDecimal newConversion = resource.getDecimalField(ResourceFields.CONVERSION);
        Entity newStorageLocation = resource.getBelongsToField(ResourceFields.STORAGE_LOCATION);
        String newBatch = resource.getStringField(ResourceFields.BATCH);
        String newTypeOfPallet = resource.getStringField(ResourceFields.TYPE_OF_PALLET);
        Date newExpirationDate = resource.getDateField(ResourceFields.EXPIRATION_DATE);
        Entity newPalletNumber = resource.getBelongsToField(ResourceFields.PALLET_NUMBER);

        if (isCorrectionNeeded(oldResource, newQuantity, newStorageLocation, newPrice, newBatch, newTypeOfPallet,
                newPalletNumber, newExpirationDate, newConversion)) {
            boolean palletNumberChanged = isPalletNumberChanged(oldPalletNumber(oldResource), newPalletNumber);
            if ((storageLocationChanged(oldResource, newStorageLocation) || palletNumberChanged) && newStorageLocation != null) {
                BigDecimal palletsInStorageLocation;
                if (newPalletNumber != null) {
                    palletsInStorageLocation = BigDecimal.valueOf(getPalletsCountInStorageLocationWithoutPalletNumber(
                            newStorageLocation, newPalletNumber) + 1);

                } else {
                    palletsInStorageLocation = BigDecimal.valueOf(getPalletsCountInStorageLocation(newStorageLocation) + 1);
                }
                BigDecimal palletsLimit = newStorageLocation.getDecimalField(StorageLocationFields.MAXIMUM_NUMBER_OF_PALLETS);
                if (palletsLimit != null && palletsInStorageLocation.compareTo(palletsLimit) > 0) {
                    resource.addGlobalError("materialFlow.error.correction.invalidStorageLocation");
                    resource.setNotValid();
                    return false;
                }

            }
            Entity correction = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                    MaterialFlowResourcesConstants.MODEL_RESOURCE_CORRECTION).create();
            BigDecimal oldQuantity = oldQuantity(oldResource);
            BigDecimal oldPrice = oldPrice(oldResource);

            correction.setField(ResourceCorrectionFields.OLD_BATCH, oldBatch(oldResource));
            correction.setField(ResourceCorrectionFields.NEW_BATCH, newBatch);
            correction.setField(ResourceCorrectionFields.LOCATION, location(oldResource));
            correction.setField(ResourceCorrectionFields.OLD_QUANTITY, oldQuantity);
            correction.setField(ResourceCorrectionFields.NEW_QUANTITY, newQuantity);
            correction.setField(ResourceCorrectionFields.OLD_PRICE, oldPrice);
            correction.setField(ResourceCorrectionFields.NEW_PRICE, newPrice);
            correction.setField(ResourceCorrectionFields.OLD_STORAGE_LOCATION, oldStorageLocation(oldResource));
            correction.setField(ResourceCorrectionFields.NEW_STORAGE_LOCATION, newStorageLocation);
            correction.setField(ResourceCorrectionFields.PRODUCT, product(oldResource));
            correction.setField(ResourceCorrectionFields.TIME, time(oldResource));
            correction.setField(ResourceCorrectionFields.NEW_TYPE_OF_PALLET, newTypeOfPallet);
            correction.setField(ResourceCorrectionFields.OLD_TYPE_OF_PALLET, oldTypeOfPallet(oldResource));
            correction.setField(ResourceCorrectionFields.OLD_EXPIRATION_DATE, oldExpirationDate(oldResource));
            correction.setField(ResourceCorrectionFields.NEW_EXPIRATION_DATE, newExpirationDate);
            correction.setField(ResourceCorrectionFields.NEW_PALLET_NUMBER, newPalletNumber);
            correction.setField(ResourceCorrectionFields.OLD_PALLET_NUMBER, oldPalletNumber(oldResource));
            correction.setField(ResourceCorrectionFields.PRODUCTION_DATE, productionDate(oldResource));
            correction.setField(ResourceCorrectionFields.OLD_CONVERSION, oldConversion(oldResource));
            correction.setField(ResourceCorrectionFields.NEW_CONVERSION, newConversion);
            correction.setField(ResourceCorrectionFields.NUMBER, numberGeneratorService.generateNumber(
                    MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER, MaterialFlowResourcesConstants.MODEL_RESOURCE_CORRECTION));

            correction.setField(ResourceCorrectionFields.RESOURCE, oldResource);
            correction.setField(ResourceCorrectionFields.RESOURCE_NUMBER, oldResource.getStringField(ResourceFields.NUMBER));
            correction.setField(ResourceCorrectionFields.DELIVERY_NUMBER,
                    oldResource.getStringField(ResourceFields.DELIVERY_NUMBER));

            resource.setField(ResourceFields.QUANTITY, newQuantity);
            resource.setField(ResourceFields.IS_CORRECTED, true);
            resource.setField(ResourceFields.QUANTITY_IN_ADDITIONAL_UNIT,
                    calculateQuantityInAdditionalUnit(resource, oldResource.getStringField(ResourceFields.GIVEN_UNIT)));
            resource.setField(ResourceFields.AVAILABLE_QUANTITY,
                    newQuantity.subtract(resource.getDecimalField(ResourceFields.RESERVED_QUANTITY)));
            Entity savedResource = resource.getDataDefinition().save(resource);
            if (savedResource.isValid()) {
                Entity savedCorrection = correction.getDataDefinition().save(correction);
                if (!savedCorrection.isValid()) {
                    throw new IllegalStateException("Could not save correction");
                }
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean isCorrectionNeeded(final Entity resource, final BigDecimal newQuantity, final Entity newStorageLocation,
            final BigDecimal newPrice, final String newBatch, final String newTypeOfPallet, final Entity newPalletNumber,
            final Date newExpirationDate, final BigDecimal newConversion) {
        boolean quantityChanged = newQuantity.compareTo(oldQuantity(resource)) != 0;
        boolean priceChanged = isPriceChanged(oldPrice(resource), newPrice);
        boolean batchChanged = isStringChanged(oldBatch(resource), newBatch);
        boolean typeOfPalletChanged = isStringChanged(oldTypeOfPallet(resource), newTypeOfPallet);
        boolean palletNumberChanged = isPalletNumberChanged(oldPalletNumber(resource), newPalletNumber);
        boolean expirationDateChanged = isExpirationDateChanged(oldExpirationDate(resource), newExpirationDate);
        boolean conversionChanged = newConversion.compareTo(oldConversion(resource)) != 0;

        boolean storageLocationChanged = storageLocationChanged(resource, newStorageLocation);
        return quantityChanged || storageLocationChanged || priceChanged || batchChanged || typeOfPalletChanged
                || palletNumberChanged || expirationDateChanged || conversionChanged;
    }

    private boolean storageLocationChanged(final Entity oldResourde, final Entity newStorageLocation) {
        Entity oldStorageLocation = oldStorageLocation(oldResourde);
        return (newStorageLocation != null && oldStorageLocation != null) ? (newStorageLocation.getId().compareTo(
                oldStorageLocation.getId()) != 0) : !(newStorageLocation == null && oldStorageLocation == null);
    }

    private Entity product(final Entity resource) {
        return resource.getBelongsToField(ResourceFields.PRODUCT);
    }

    private BigDecimal oldQuantity(final Entity resource) {
        return resource.getDecimalField(ResourceFields.QUANTITY);
    }

    private BigDecimal oldPrice(final Entity resource) {
        return resource.getDecimalField(ResourceFields.PRICE);
    }

    private BigDecimal oldConversion(final Entity resource) {
        return resource.getDecimalField(ResourceFields.CONVERSION);
    }

    private Entity location(final Entity resource) {
        return resource.getBelongsToField(ResourceFields.LOCATION);
    }

    private Date time(final Entity resource) {
        return resource.getDateField(ResourceFields.TIME);
    }

    private String oldBatch(final Entity resource) {
        return resource.getStringField(ResourceFields.BATCH);
    }

    private String oldTypeOfPallet(final Entity resource) {
        return resource.getStringField(ResourceFields.TYPE_OF_PALLET);
    }

    private Entity oldPalletNumber(final Entity resource) {
        return resource.getBelongsToField(ResourceFields.PALLET_NUMBER);
    }

    private Date oldExpirationDate(final Entity resource) {
        return resource.getDateField(ResourceFields.EXPIRATION_DATE);
    }

    private Date productionDate(final Entity resource) {
        return resource.getDateField(ResourceFields.PRODUCTION_DATE);
    }

    private Entity oldStorageLocation(final Entity resource) {
        return resource.getBelongsToField(ResourceFields.STORAGE_LOCATION);
    }

    private BigDecimal calculateQuantityInAdditionalUnit(Entity resource, String unit) {
        BigDecimal conversion = resource.getDecimalField(ResourceFields.CONVERSION);
        BigDecimal quantity = resource.getDecimalField(ResourceFields.QUANTITY);

        boolean isInteger = dictionaryService.checkIfUnitIsInteger(unit);
        BigDecimal value = quantity.multiply(conversion, numberService.getMathContext());
        if (isInteger) {
            return numberService.setScaleWithDefaultMathContext(value, 0);
        } else {
            return value;
        }
    }

    private boolean isPriceChanged(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null && newPrice == null) {
            return false;
        }
        if (oldPrice == null || newPrice == null) {
            return true;
        }
        return oldPrice.compareTo(newPrice) != 0;
    }

    private boolean isStringChanged(String oldString, String newString) {
        if (Strings.isNullOrEmpty(oldString) && Strings.isNullOrEmpty(newString)) {
            return false;
        }
        if (Strings.isNullOrEmpty(oldString) || Strings.isNullOrEmpty(newString)) {
            return true;
        }
        return oldString.compareTo(newString) != 0;
    }

    private boolean isPalletNumberChanged(Entity oldPalletNumber, Entity newPalletNumber) {
        if (oldPalletNumber == null && newPalletNumber == null) {
            return false;
        }
        if (oldPalletNumber == null || newPalletNumber == null) {
            return true;
        }
        return oldPalletNumber.getId().compareTo(newPalletNumber.getId()) != 0;
    }

    private boolean isExpirationDateChanged(Date oldExpirationDate, Date newExpirationDate) {
        if (oldExpirationDate == null && newExpirationDate == null) {
            return false;
        }
        if (oldExpirationDate == null || newExpirationDate == null) {
            return true;
        }
        return oldExpirationDate.compareTo(newExpirationDate) != 0;
    }

}
