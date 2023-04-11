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
package com.qcadoo.mes.operationCostCalculations;

import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.costNormsForOperation.constants.CalculationOperationComponentFields;
import com.qcadoo.mes.costNormsForOperation.constants.TechnologyOperationComponentFieldsCNFO;
import com.qcadoo.mes.operationTimeCalculations.OperationWorkTime;
import com.qcadoo.mes.operationTimeCalculations.OperationWorkTimeService;
import com.qcadoo.mes.operationTimeCalculations.dto.OperationTimes;
import com.qcadoo.mes.operationTimeCalculations.dto.OperationTimesContainer;
import com.qcadoo.mes.technologies.ProductQuantitiesService;
import com.qcadoo.mes.technologies.ProductionLinesService;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.mes.technologies.dto.ProductQuantitiesHolder;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityTree;
import com.qcadoo.model.api.EntityTreeNode;
import com.qcadoo.model.api.IntegerUtils;
import com.qcadoo.model.api.NumberService;

@Service
public class OperationsCostCalculationServiceImpl implements OperationsCostCalculationService {

    private static final String L_PRODUCTION_BALANCE = "productionBalance";

    private static final String L_COST_CALCULATION = "costCalculation";

    private static final String L_CALCULATION_OPERATION_COMPONENTS = "calculationOperationComponents";

    private static final String L_ORDER = "order";

    private static final String L_TECHNOLOGY = "technology";

    private static final String L_QUANTITY = "quantity";

    private static final String L_PRODUCTION_COST_MARGIN = "productionCostMargin";

    private static final String L_PRODUCTION_LINE = "productionLine";

    private static final String L_INCLUDE_ADDITIONAL_TIME = "includeAdditionalTime";

    private static final String L_INCLUDE_TPZ = "includeTPZ";

    private static final String L_TOTAL_LABOR_HOURLY_COSTS = "totalLaborHourlyCosts";

    private static final String L_TOTAL_MACHINE_HOURLY_COSTS = "totalMachineHourlyCosts";

    private static final String L_TOTAL_PIECEWORK_COSTS = "totalPieceworkCosts";

    private static final String L_MACHINE_HOURLY_COST = "machineHourlyCost";

    private static final String L_LABOR_HOURLY_COST = "laborHourlyCost";

    private static final String L_OPERATION_MACHINE_COST = "operationMachineCost";

    private static final String L_OPERATION_LABOR_COST = "operationLaborCost";

    private static final String L_OPERATION_COST = "operationCost";

    private static final String L_OPERATION_MARGIN_COST = "operationMarginCost";

    private static final String L_TOTAL_OPERATION_COST = "totalOperationCost";

    private static final String L_PIECES = "pieces";

    private static final String L_TOTAL_LABOR_OPERATION_COST_WITH_MARGIN = "totalLaborOperationCostWithMargin";

    private static final String L_TOTAL_MACHINE_OPERATION_COST_WITH_MARGIN = "totalMachineOperationCostWithMargin";

    private static final Set<String> L_COST_KEYS = Sets.newHashSet(CalculationOperationComponentFields.LABOR_HOURLY_COST,
            CalculationOperationComponentFields.MACHINE_HOURLY_COST);

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private ProductQuantitiesService productQuantitiesService;

    @Autowired
    private ProductionLinesService productionLinesService;

    @Autowired
    private OperationWorkTimeService operationWorkTimeService;

    @Autowired
    private OperationCostCalculationTreeBuilder operationCostCalculationTreeBuilder;

    @Autowired
    private ParameterService parameterService;

    @Override
    public void calculateOperationsCost(final Entity costCalculationOrProductionBalance, boolean hourlyCostFromOperation) {
        checkArgument(costCalculationOrProductionBalance != null, "entity is null");
        String modelName = costCalculationOrProductionBalance.getDataDefinition().getName();
        checkArgument(L_COST_CALCULATION.equals(modelName) || L_PRODUCTION_BALANCE.equals(modelName), "unsupported entity type");

        DataDefinition costCalculationOrProductionBalanceDD = costCalculationOrProductionBalance.getDataDefinition();

        Entity order = costCalculationOrProductionBalance.getBelongsToField(L_ORDER);
        Entity technology = costCalculationOrProductionBalance.getBelongsToField(L_TECHNOLOGY);
        BigDecimal quantity = BigDecimalUtils.convertNullToZero(costCalculationOrProductionBalance.getDecimalField(L_QUANTITY));
        BigDecimal productionCostMargin = BigDecimalUtils.convertNullToZero(costCalculationOrProductionBalance
                .getDecimalField(L_PRODUCTION_COST_MARGIN));

        if (order != null) {
            Entity technologyFromOrder = order.getBelongsToField(L_TECHNOLOGY);

            technology = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                    TechnologiesConstants.MODEL_TECHNOLOGY).get(technologyFromOrder.getId());
        }

        ProductQuantitiesHolder productQuantitiesAndOperationRuns = getProductQuantitiesAndOperationRuns(technology, quantity,
                costCalculationOrProductionBalance);

        if (order != null) {
            order.setField(L_TECHNOLOGY, technology);
        }
        Entity copyCostCalculationOrProductionBalance = operationCostCalculationTreeBuilder
                .copyTechnologyTree(costCalculationOrProductionBalance);

        Entity yetAnotherCostCalculationOrProductionBalance = costCalculationOrProductionBalanceDD
                .save(copyCostCalculationOrProductionBalance);
        Entity newCostCalculationOrProductionBalance = costCalculationOrProductionBalanceDD
                .get(yetAnotherCostCalculationOrProductionBalance.getId());

        EntityTree calculationOperationComponents = newCostCalculationOrProductionBalance
                .getTreeField(L_CALCULATION_OPERATION_COMPONENTS);

        checkArgument(calculationOperationComponents != null, "given operation components is null");

        Entity productionLine = costCalculationOrProductionBalance.getBelongsToField(L_PRODUCTION_LINE);

        Boolean includeTPZ = costCalculationOrProductionBalance.getBooleanField(L_INCLUDE_TPZ);
        Boolean includeAdditionalTime = costCalculationOrProductionBalance.getBooleanField(L_INCLUDE_ADDITIONAL_TIME);

        Map<Long, Integer> workstations = getWorkstationsMapsForOperationsComponent(copyCostCalculationOrProductionBalance,
                productionLine);

        List<Entity> tocs = calculationOperationComponents.stream().map(e -> e.getBelongsToField("technologyOperationComponent"))
                .collect(Collectors.toList());
        OperationTimesContainer operationTimes = operationWorkTimeService.estimateOperationsWorkTimes(tocs,
                productQuantitiesAndOperationRuns.getOperationRuns(), includeTPZ, includeAdditionalTime, workstations, true);

        Map<String, BigDecimal> resultsMap = estimateCostCalculationForHourly(calculationOperationComponents.getRoot(),
                productionCostMargin, quantity, operationTimes, hourlyCostFromOperation);

        costCalculationOrProductionBalance.setField(L_TOTAL_MACHINE_HOURLY_COSTS, numberService
                .setScaleWithDefaultMathContext(resultsMap.get(CalculationOperationComponentFields.MACHINE_HOURLY_COST)));
        costCalculationOrProductionBalance.setField(L_TOTAL_LABOR_HOURLY_COSTS, numberService
                .setScaleWithDefaultMathContext(resultsMap.get(CalculationOperationComponentFields.LABOR_HOURLY_COST)));

        costCalculationOrProductionBalance.setField(L_CALCULATION_OPERATION_COMPONENTS, calculationOperationComponents);
    }

    private ProductQuantitiesHolder getProductQuantitiesAndOperationRuns(final Entity technology, final BigDecimal quantity,
            final Entity costCalculationOrProductionBalance) {
        return productQuantitiesService.getProductComponentQuantities(technology, quantity);
    }

    @Override
    public Map<String, BigDecimal> estimateCostCalculationForHourly(final EntityTreeNode calculationOperationComponent,
            final BigDecimal productionCostMargin, final BigDecimal plannedQuantity,
            final OperationTimesContainer realizationTimes, final boolean hourlyCostFromOperation) {
        checkArgument(calculationOperationComponent != null, "given operationComponent is empty");

        Map<String, BigDecimal> costs = Maps.newHashMapWithExpectedSize(L_COST_KEYS.size());

        MathContext mathContext = numberService.getMathContext();

        for (String costKey : L_COST_KEYS) {
            costs.put(costKey, BigDecimal.ZERO);
        }

        for (EntityTreeNode child : calculationOperationComponent.getChildren()) {
            Map<String, BigDecimal> unitCosts = estimateCostCalculationForHourly(child, productionCostMargin, plannedQuantity,
                    realizationTimes, hourlyCostFromOperation);

            for (String costKey : L_COST_KEYS) {
                BigDecimal unitCost = costs.get(costKey).add(unitCosts.get(costKey), mathContext);

                costs.put(costKey, numberService.setScaleWithDefaultMathContext(unitCost));
            }
        }

        OperationTimes operationTimes = realizationTimes.get(calculationOperationComponent.getBelongsToField(
                "technologyOperationComponent").getId());
        Map<String, BigDecimal> costsForSingleOperation = estimateHourlyCostCalculationForSingleOperation(operationTimes,
                productionCostMargin, hourlyCostFromOperation);
        saveGeneratedValues(costsForSingleOperation, calculationOperationComponent, true, operationTimes.getTimes(), null);

        costs.put(L_MACHINE_HOURLY_COST,
                costs.get(L_MACHINE_HOURLY_COST).add(costsForSingleOperation.get(L_OPERATION_MACHINE_COST), mathContext));
        costs.put(L_LABOR_HOURLY_COST,
                costs.get(L_LABOR_HOURLY_COST).add(costsForSingleOperation.get(L_OPERATION_LABOR_COST), mathContext));

        return costs;
    }

    private Map<String, BigDecimal> estimateHourlyCostCalculationForSingleOperation(final OperationTimes operationTimes,
            final BigDecimal productionCostMargin, boolean hourlyCostFromOperation) {
        Map<String, BigDecimal> costs = Maps.newHashMap();

        MathContext mathContext = numberService.getMathContext();

        Entity technologyOperationComponent = operationTimes.getOperation();

        OperationWorkTime operationWorkTimes = operationTimes.getTimes();

        BigDecimal machineHourlyCost = BigDecimal.ZERO;
        BigDecimal laborHourlyCost = BigDecimal.ZERO;
        if (hourlyCostFromOperation) {
            machineHourlyCost = BigDecimalUtils.convertNullToZero(technologyOperationComponent
                    .getField(TechnologyOperationComponentFieldsCNFO.MACHINE_HOURLY_COST));
            laborHourlyCost = BigDecimalUtils.convertNullToZero(technologyOperationComponent
                    .getField(TechnologyOperationComponentFieldsCNFO.LABOR_HOURLY_COST));
        } else {
            machineHourlyCost = BigDecimalUtils.convertNullToZero(parameterService.getParameter().getDecimalField(
                    "averageMachineHourlyCostPB"));
            laborHourlyCost = BigDecimalUtils.convertNullToZero(parameterService.getParameter().getDecimalField(
                    "averageLaborHourlyCostPB"));
        }

        BigDecimal durationMachine = BigDecimal.valueOf(operationWorkTimes.getMachineWorkTime());
        BigDecimal durationLabor = BigDecimal.valueOf(operationWorkTimes.getLaborWorkTime());

        BigDecimal durationMachineInHours = durationMachine.divide(BigDecimal.valueOf(3600), mathContext);
        BigDecimal durationLaborInHours = durationLabor.divide(BigDecimal.valueOf(3600), mathContext);

        BigDecimal operationMachineCost = durationMachineInHours.multiply(machineHourlyCost, mathContext);
        BigDecimal operationLaborCost = durationLaborInHours.multiply(laborHourlyCost, mathContext);

        BigDecimal totalMachineOperationCostWithMargin = operationMachineCost.add(
                operationMachineCost.multiply(productionCostMargin.divide(BigDecimal.valueOf(100), mathContext), mathContext),
                mathContext);

        BigDecimal totalLaborOperationCostWithMargin = operationLaborCost.add(
                operationLaborCost.multiply(productionCostMargin.divide(BigDecimal.valueOf(100), mathContext), mathContext),
                mathContext);

        BigDecimal operationCost = operationMachineCost.add(operationLaborCost, mathContext);
        BigDecimal operationMarginCost = operationCost.multiply(
                productionCostMargin.divide(BigDecimal.valueOf(100), mathContext), mathContext);

        costs.put(L_MACHINE_HOURLY_COST, numberService.setScaleWithDefaultMathContext(machineHourlyCost));
        costs.put(L_LABOR_HOURLY_COST, numberService.setScaleWithDefaultMathContext(laborHourlyCost));
        costs.put(L_OPERATION_MACHINE_COST, numberService.setScaleWithDefaultMathContext(operationMachineCost));
        costs.put(L_OPERATION_LABOR_COST, numberService.setScaleWithDefaultMathContext(operationLaborCost));
        costs.put(L_OPERATION_COST, numberService.setScaleWithDefaultMathContext(operationCost));
        costs.put(L_OPERATION_MARGIN_COST, numberService.setScaleWithDefaultMathContext(operationMarginCost));
        costs.put(L_TOTAL_MACHINE_OPERATION_COST_WITH_MARGIN,
                numberService.setScaleWithDefaultMathContext(totalMachineOperationCostWithMargin));
        costs.put(L_TOTAL_LABOR_OPERATION_COST_WITH_MARGIN,
                numberService.setScaleWithDefaultMathContext(totalLaborOperationCostWithMargin));

        return costs;
    }

    private Map<String, BigDecimal> estimatePieceworkCostCalculationForSingleOperation(
            final EntityTreeNode calculationOperationComponent, final BigDecimal productionCostMargin,
            final BigDecimal operationRuns) {
        Map<String, BigDecimal> costs = Maps.newHashMap();

        BigDecimal pieceworkCost = BigDecimalUtils.convertNullToZero(calculationOperationComponent
                .getDecimalField(CalculationOperationComponentFields.PIECEWORK_COST));
        BigDecimal numberOfOperations = BigDecimalUtils.convertNullToOne(calculationOperationComponent
                .getField(CalculationOperationComponentFields.NUMBER_OF_OPERATIONS));

        BigDecimal pieceworkCostPerOperation = pieceworkCost.divide(numberOfOperations, numberService.getMathContext());

        BigDecimal operationCost = operationRuns.multiply(pieceworkCostPerOperation, numberService.getMathContext());
        BigDecimal operationMarginCost = operationCost.multiply(productionCostMargin.divide(BigDecimal.valueOf(100),
                numberService.getMathContext()));
        BigDecimal totalOperationCost = numberService.setScaleWithDefaultMathContext(operationCost.add(operationMarginCost,
                numberService.getMathContext()));

        costs.put(L_OPERATION_COST, numberService.setScaleWithDefaultMathContext(operationCost));
        costs.put(L_OPERATION_MARGIN_COST, numberService.setScaleWithDefaultMathContext(operationMarginCost));
        costs.put(L_PIECES, numberService.setScaleWithDefaultMathContext(operationRuns));
        costs.put(L_TOTAL_OPERATION_COST, totalOperationCost);

        return costs;
    }

    private void saveGeneratedValues(final Map<String, BigDecimal> costs, final Entity calculationOperationComponent,
            boolean areHourly, final OperationWorkTime operationWorkTimes, final BigDecimal operationRuns) {
        if (areHourly) {
            calculationOperationComponent.setField(CalculationOperationComponentFields.DURATION, new BigDecimal(
                    operationWorkTimes.getDuration(), numberService.getMathContext()));
            calculationOperationComponent.setField(CalculationOperationComponentFields.MACHINE_HOURLY_COST,
                    costs.get(L_MACHINE_HOURLY_COST));
            calculationOperationComponent.setField(CalculationOperationComponentFields.LABOR_HOURLY_COST,
                    costs.get(L_LABOR_HOURLY_COST));
            calculationOperationComponent.setField(CalculationOperationComponentFields.TOTAL_MACHINE_OPERATION_COST,
                    costs.get(L_OPERATION_MACHINE_COST));
            calculationOperationComponent.setField(CalculationOperationComponentFields.TOTAL_LABOR_OPERATION_COST,
                    costs.get(L_OPERATION_LABOR_COST));
            calculationOperationComponent.setField(CalculationOperationComponentFields.TOTAL_MACHINE_OPERATION_COST_WITH_MARGIN,
                    costs.get(L_TOTAL_MACHINE_OPERATION_COST_WITH_MARGIN));
            calculationOperationComponent.setField(CalculationOperationComponentFields.TOTAL_LABOR_OPERATION_COST_WITH_MARGIN,
                    costs.get(L_TOTAL_LABOR_OPERATION_COST_WITH_MARGIN));
        } else {
            calculationOperationComponent.setField(CalculationOperationComponentFields.PIECES,
                    numberService.setScaleWithDefaultMathContext(operationRuns));
        }

        BigDecimal operationCost = costs.get(L_OPERATION_COST);
        BigDecimal operationMarginCost = costs.get(L_OPERATION_MARGIN_COST);

        calculationOperationComponent.setField(CalculationOperationComponentFields.OPERATION_COST,
                numberService.setScaleWithDefaultMathContext(operationCost));
        calculationOperationComponent.setField(CalculationOperationComponentFields.OPERATION_MARGIN_COST,
                numberService.setScaleWithDefaultMathContext(operationMarginCost));
        calculationOperationComponent.setField(
                CalculationOperationComponentFields.TOTAL_OPERATION_COST,
                numberService.setScaleWithDefaultMathContext(operationCost.add(operationMarginCost,
                        numberService.getMathContext())));

        calculationOperationComponent.getDataDefinition().save(calculationOperationComponent);
    }

    private Map<Long, Integer> getWorkstationsMapsForOperationsComponent(final Entity costCalculationOrProductionBalance,
            final Entity productionLine) {
        Entity order = costCalculationOrProductionBalance.getBelongsToField(L_ORDER);
        if (order == null) {
            return getWorkstationsFromTechnology(costCalculationOrProductionBalance.getBelongsToField(L_TECHNOLOGY),
                    productionLine);
        } else {
            return getWorkstationsFromOrder(order);
        }
    }

    private Map<Long, Integer> getWorkstationsFromTechnology(final Entity technology, final Entity productionLine) {
        Map<Long, Integer> workstations = Maps.newHashMap();
        if (parameterService.getParameter().getBooleanField("workstationsQuantityFromProductionLine")) {
            for (Entity operComp : technology.getHasManyField(TechnologyFields.OPERATION_COMPONENTS)) {
                workstations.put(operComp.getId(), productionLinesService.getWorkstationTypesCount(operComp, productionLine));
            }
        } else {
            for (Entity operComp : technology.getHasManyField(TechnologyFields.OPERATION_COMPONENTS)) {
                workstations.put(operComp.getId(), IntegerUtils.convertNullToZero(operComp
                        .getIntegerField(TechnologyOperationComponentFields.QUANTITY_OF_WORKSTATIONS)));
            }
        }
        return workstations;
    }

    private Map<Long, Integer> getWorkstationsFromOrder(final Entity order) {
        Map<Long, Integer> workstations = Maps.newHashMap();

        for (Entity technologyOperationComponent : order.getBelongsToField(L_TECHNOLOGY).getHasManyField(
                TechnologyFields.OPERATION_COMPONENTS)) {
            workstations.put(technologyOperationComponent.getId(), IntegerUtils.convertNullToZero(technologyOperationComponent
                    .getIntegerField(TechnologyOperationComponentFields.QUANTITY_OF_WORKSTATIONS)));
        }

        return workstations;
    }

}