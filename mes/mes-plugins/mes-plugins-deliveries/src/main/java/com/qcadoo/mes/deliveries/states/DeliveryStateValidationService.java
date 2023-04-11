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
package com.qcadoo.mes.deliveries.states;

import static com.google.common.base.Preconditions.checkArgument;
import static com.qcadoo.mes.deliveries.constants.DeliveryFields.DELIVERED_PRODUCTS;
import static com.qcadoo.mes.deliveries.constants.DeliveryFields.DELIVERY_DATE;
import static com.qcadoo.mes.deliveries.constants.DeliveryFields.LOCATION;
import static com.qcadoo.mes.deliveries.constants.DeliveryFields.SUPPLIER;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.deliveries.ProductSynchronizationService;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.mes.states.StateChangeContext;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.Entity;
import com.qcadoo.plugin.api.PluginManager;

@Service
public class DeliveryStateValidationService {

    private static final String ENTITY_IS_NULL = "entity is null";

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private PluginManager pluginManager;

    @Autowired
    private ProductSynchronizationService productSynchronizationService;

    public void validationOnApproved(final StateChangeContext stateChangeContext) {
        final List<String> references = Lists.newArrayList(DELIVERY_DATE, SUPPLIER);

        checkRequired(references, stateChangeContext);
        checkOrderedQuantity(stateChangeContext);
    }

    public void validationOnReceived(final StateChangeContext stateChangeContext) {
        final List<String> references = Lists.newArrayList(LOCATION);

        checkRequired(references, stateChangeContext);
        checkDeliveredQuantity(stateChangeContext);

        if (parameterService.getParameter().getBooleanField("positivePurchasePrice")) {
            checkDeliveredPurchasePrices(stateChangeContext);
        }

        if (pluginManager.isPluginEnabled("integration") && productSynchronizationService.shouldSynchronize(stateChangeContext)) {
            checkDeliveredProductsSynchronizationStatus(stateChangeContext);
            productSynchronizationService.synchronizeProducts(stateChangeContext, false);
        }
    }

    private void checkDeliveredProductsSynchronizationStatus(final StateChangeContext stateChangeContext) {
        final Set<String> notSynchronizedOrderedProducts = stateChangeContext.getOwner()
                .getHasManyField(DeliveryFields.DELIVERED_PRODUCTS).stream()
                .map(productsHolder -> productsHolder.getBelongsToField(DeliveredProductFields.PRODUCT))
                .filter(product -> isBlank(product.getStringField(ProductFields.EXTERNAL_NUMBER)))
                .map(product -> product.getStringField(ProductFields.NUMBER)).collect(Collectors.toSet());

        if (!notSynchronizedOrderedProducts.isEmpty()) {
            stateChangeContext.addValidationError("deliveries.deliveredProducts.notSynchronized", false,
                    String.join(", ", notSynchronizedOrderedProducts));
        }
    }

    public void checkRequired(final List<String> fieldNames, final StateChangeContext stateChangeContext) {
        checkArgument(stateChangeContext != null, ENTITY_IS_NULL);

        final Entity stateChangeEntity = stateChangeContext.getOwner();

        for (String fieldName : fieldNames) {
            if (stateChangeEntity.getField(fieldName) == null) {
                stateChangeContext.addFieldValidationError(fieldName, "deliveries.delivery.deliveryStates.fieldRequired");
            }
        }
    }

    public void checkDeliveredQuantity(final StateChangeContext stateChangeContext) {
        checkArgument(stateChangeContext != null, ENTITY_IS_NULL);

        final Entity stateChangeEntity = stateChangeContext.getOwner();
        List<Entity> deliveredProducts = stateChangeEntity.getHasManyField(DELIVERED_PRODUCTS);
        boolean deliveredProductHasNull = false;

        if (deliveredProducts.isEmpty()) {
            stateChangeContext.addValidationError("deliveries.deliveredProducts.deliveredProductsList.isEmpty");
        }

        StringBuffer listOfProductNumber = new StringBuffer();

        for (Entity delivProd : deliveredProducts) {
            if (delivProd.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY) == null) {
                deliveredProductHasNull = true;

                listOfProductNumber
                        .append(delivProd.getBelongsToField(DeliveredProductFields.PRODUCT).getStringField(ProductFields.NUMBER));
                listOfProductNumber.append(", ");
            }
        }

        if (deliveredProductHasNull) {
            stateChangeContext.addValidationError("deliveries.deliveredProducts.deliveredQuantity.isRequired", false,
                    listOfProductNumber.toString());
        }
    }

    private void checkOrderedQuantity(StateChangeContext stateChangeContext) {
        final Entity stateChangeEntity = stateChangeContext.getOwner();
        List<Entity> orderedProducts = stateChangeEntity.getHasManyField(DeliveryFields.ORDERED_PRODUCTS);

        StringBuffer listOfProductNumber = new StringBuffer();

        orderedProducts.forEach((orderedProduct) -> {
            BigDecimal orderedQuantity = BigDecimalUtils
                    .convertNullToZero(orderedProduct.getDecimalField(OrderedProductFields.ORDERED_QUANTITY));

            if (orderedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                if (!listOfProductNumber.toString().isEmpty()) {
                    listOfProductNumber.append(", ");
                }

                listOfProductNumber.append(
                        orderedProduct.getBelongsToField(DeliveredProductFields.PRODUCT).getStringField(ProductFields.NUMBER));
            }
        });

        if (!listOfProductNumber.toString().isEmpty()) {
            stateChangeContext.addValidationError("deliveries.orderedProducts.orderedQuantity.isRequired", false,
                    listOfProductNumber.toString());
        }
    }

    private void checkDeliveredPurchasePrices(StateChangeContext stateChangeContext) {
        final Entity stateChangeEntity = stateChangeContext.getOwner();
        List<Entity> deliveredProducts = stateChangeEntity.getHasManyField(DeliveryFields.DELIVERED_PRODUCTS);

        StringBuffer listOfProductNumber = new StringBuffer();

        deliveredProducts.forEach((deliveredProduct) -> {
            BigDecimal price = deliveredProduct.getDecimalField(DeliveredProductFields.PRICE_PER_UNIT);

            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                if (!listOfProductNumber.toString().isEmpty()) {
                    listOfProductNumber.append(", ");
                }

                listOfProductNumber.append(
                        deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT).getStringField(ProductFields.NUMBER));
            }
        });

        if (!listOfProductNumber.toString().isEmpty()) {
            stateChangeContext.addValidationError("deliveries.deliveredProducts.deliveredPurchasePrice.isRequired", false,
                    listOfProductNumber.toString());
        }
    }

}
