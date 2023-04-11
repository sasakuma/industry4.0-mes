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
package com.qcadoo.mes.costNormsForMaterials;

import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.qcadoo.mes.basicProductionCounting.BasicProductionCountingService;
import com.qcadoo.mes.basicProductionCounting.constants.ProductionCountingQuantityFields;
import com.qcadoo.mes.basicProductionCounting.constants.ProductionCountingQuantityRole;
import com.qcadoo.mes.basicProductionCounting.constants.ProductionCountingQuantityTypeOfMaterial;
import com.qcadoo.mes.costNormsForMaterials.constants.OrderFieldsCNFM;
import com.qcadoo.mes.costNormsForMaterials.constants.ProductsCostFields;
import com.qcadoo.mes.costNormsForMaterials.constants.TechnologyInstOperProductInCompFields;
import com.qcadoo.mes.costNormsForMaterials.orderRawMaterialCosts.dataProvider.OrderMaterialCostsDataProvider;
import com.qcadoo.mes.orders.states.constants.OrderState;
import com.qcadoo.mes.technologies.ProductQuantitiesService;
import com.qcadoo.mes.technologies.constants.MrpAlgorithm;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;

@Service
public class ProductsCostCalculationServiceImpl implements ProductsCostCalculationService {

    @Autowired
    private ProductQuantitiesService productQuantitiesService;

    @Autowired
    private OrderMaterialCostsDataProvider orderMaterialCostsDataProvider;

    @Autowired
    private NumberService numberService;

    @Autowired
    private BasicProductionCountingService basicProductionCountingService;

    @Override
    public void calculateTotalProductsCost(final Entity entity, final String sourceOfMaterialCosts) {
        Map<Entity, BigDecimal> listProductWithCost = calculateListProductsCostForPlannedQuantity(entity, sourceOfMaterialCosts);
        BigDecimal result = BigDecimal.ZERO;
        for (Entry<Entity, BigDecimal> productWithCost : listProductWithCost.entrySet()) {
            result = result.add(productWithCost.getValue(), numberService.getMathContext());
        }
        entity.setField("totalMaterialCosts", numberService.setScaleWithDefaultMathContext(result));
    }

    private Map<Entity, BigDecimal> calculateListProductsCostForPlannedQuantity(final Entity entity,
            final String sourceOfMaterialCosts) {
        checkArgument(entity != null);
        BigDecimal quantity = BigDecimalUtils.convertNullToZero(entity.getDecimalField("quantity"));

        String calculateMaterialCostsMode = entity.getStringField("calculateMaterialCostsMode");

        checkArgument(calculateMaterialCostsMode != null, "calculateMaterialCostsMode is null!");

        Entity technology = entity.getBelongsToField("technology");

        Entity order = entity.getBelongsToField("order");

        if ("02fromOrdersMaterialCosts".equals(sourceOfMaterialCosts)) {
            return getProductWithCostForPlannedQuantities(technology, quantity, calculateMaterialCostsMode, order);
        } else if ("01currentGlobalDefinitionsInProduct".equals(sourceOfMaterialCosts)) {
            return getProductWithCostForPlannedQuantities(entity, technology, quantity, calculateMaterialCostsMode);
        }

        throw new IllegalStateException("sourceOfProductCosts is neither FROM_ORDER nor GLOBAL");
    }

    @Override
    public BigDecimal calculateProductCostForGivenQuantity(final Entity product, final BigDecimal quantity,
            final String calculateMaterialCostsMode) {
        BigDecimal cost = BigDecimalUtils.convertNullToZero(product.getField(ProductsCostFields.forMode(
                calculateMaterialCostsMode).getStrValue()));
        BigDecimal costForNumber = BigDecimalUtils.convertNullToOne(product.getDecimalField("costForNumber"));
        if (BigDecimalUtils.valueEquals(costForNumber, BigDecimal.ZERO)) {
            costForNumber = BigDecimal.ONE;
        }
        BigDecimal costPerUnit = cost.divide(costForNumber, numberService.getMathContext());

        return costPerUnit.multiply(quantity, numberService.getMathContext());
    }

    private Map<Entity, BigDecimal> getProductWithCostForPlannedQuantities(final Entity entity, final Entity technology,
                                                                           final BigDecimal quantity, final String calculateMaterialCostsMode) {
        Map<Long, BigDecimal> neededProductQuantities = getNeededProductQuantities(entity, technology, quantity,
                MrpAlgorithm.ONLY_COMPONENTS);
        Map<Entity, BigDecimal> results = new HashMap<>();
        for (Entry<Long, BigDecimal> productQuantity : neededProductQuantities.entrySet()) {
            Entity product = productQuantitiesService.getProduct(productQuantity.getKey());
            BigDecimal thisProductsCost = calculateProductCostForGivenQuantity(product, productQuantity.getValue(),
                    calculateMaterialCostsMode);
            results.put(product, thisProductsCost);
        }
        return results;
    }

    private Map<Long, BigDecimal> getNeededProductQuantities(final Entity entity, final Entity technology,
            final BigDecimal quantity, final MrpAlgorithm algorithm) {
        return productQuantitiesService.getNeededProductQuantities(technology, quantity, algorithm);
    }

    private Map<Entity, BigDecimal> getProductWithCostForPlannedQuantities(final Entity technology, final BigDecimal quantity,
                                                                           final String calculateMaterialCostsMode, final Entity order) {
        Map<Entity, BigDecimal> results = Maps.newHashMap();
        if (OrderState.PENDING.equals(OrderState.of(order))) {
            Map<Long, BigDecimal> neededProductQuantities = productQuantitiesService.getNeededProductQuantities(technology,
                    quantity, MrpAlgorithm.ONLY_COMPONENTS);

            for (Entry<Long, BigDecimal> productQuantity : neededProductQuantities.entrySet()) {
                Entity product = productQuantitiesService.getProduct(productQuantity.getKey());
                for (Entity orderMaterialCosts : findOrderMaterialCosts(order, product).asSet()) {
                    BigDecimal thisProductsCost = calculateProductCostForGivenQuantity(orderMaterialCosts,
                            productQuantity.getValue(), calculateMaterialCostsMode);
                    results.put(product, thisProductsCost);
                }
            }
        } else {

            List<Entity> usedMaterials = basicProductionCountingService.getUsedMaterialsFromProductionCountingQuantities(order);
            usedMaterials = usedMaterials
                    .stream()
                    .filter(material -> material.getStringField(ProductionCountingQuantityFields.ROLE).equals(
                            ProductionCountingQuantityRole.USED.getStringValue())
                            && material.getStringField(ProductionCountingQuantityFields.TYPE_OF_MATERIAL).equals(
                                    ProductionCountingQuantityTypeOfMaterial.COMPONENT.getStringValue()))
                    .collect(Collectors.toList());
            List<Entity> allOrderMaterialCosts = order.getHasManyField(OrderFieldsCNFM.TECHNOLOGY_INST_OPER_PRODUCT_IN_COMPS);
            for (Entity usedMaterial : usedMaterials) {
                Entity product = usedMaterial.getBelongsToField(ProductionCountingQuantityFields.PRODUCT);
                for (Entity orderMaterialCosts : allOrderMaterialCosts
                        .stream()
                        .filter(cost -> cost.getBelongsToField(TechnologyInstOperProductInCompFields.PRODUCT).getId()
                                .equals(product.getId())).collect(Collectors.toList())) {
                    BigDecimal thisProductsCost = calculateProductCostForGivenQuantity(orderMaterialCosts,
                            usedMaterial.getDecimalField(ProductionCountingQuantityFields.PLANNED_QUANTITY),
                            calculateMaterialCostsMode);
                    results.put(product, thisProductsCost);
                }
            }
        }
        return results;
    }

    @Override
    public Entity getAppropriateCostNormForProduct(final Entity product, final Entity order, final String sourceOfMaterialCosts) {
        if ("01currentGlobalDefinitionsInProduct".equals(sourceOfMaterialCosts)) {
            return product;
        }
        for (Entity orderMaterialCosts : findOrderMaterialCosts(order, product).asSet()) {
            return orderMaterialCosts;
        }
        throw new IllegalStateException("Product with number=" + product.getId() + " doesn't exists for order with id="
                + order.getId());
    }

    private Optional<Entity> findOrderMaterialCosts(final Entity order, final Entity product) {
        return orderMaterialCostsDataProvider.find(order.getId(), product.getId());
    }
}
