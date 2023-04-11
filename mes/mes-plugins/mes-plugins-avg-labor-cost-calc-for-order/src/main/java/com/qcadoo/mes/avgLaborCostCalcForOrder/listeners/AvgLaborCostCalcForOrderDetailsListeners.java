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
package com.qcadoo.mes.avgLaborCostCalcForOrder.listeners;

import static com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields.AVERAGE_LABOR_HOURLY_COST;
import static com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields.BASED_ON;
import static com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields.FINISH_DATE;
import static com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields.ORDER;
import static com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields.PRODUCTION_LINE;
import static com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields.START_DATE;
import static com.qcadoo.mes.costNormsForOperation.constants.TechnologyOperationComponentFieldsCNFO.LABOR_HOURLY_COST;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.localization.api.utils.DateUtils;
import com.qcadoo.mes.avgLaborCostCalcForOrder.AverageCostService;
import com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderConstants;
import com.qcadoo.mes.avgLaborCostCalcForOrder.constants.AvgLaborCostCalcForOrderFields;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;

@Service
public class AvgLaborCostCalcForOrderDetailsListeners {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private AverageCostService averageCostService;

    public void setAvgLaborCostCalcForGivenOrder(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (args.length < 2) {
            return;
        }
        Long sourceId = Long.valueOf(args[1]);
        DataDefinition avgLaborCostCalcForOrderDD = dataDefinitionService.get(
                AvgLaborCostCalcForOrderConstants.PLUGIN_IDENTIFIER,
                AvgLaborCostCalcForOrderConstants.MODEL_AVG_LABOR_COST_CALC_FOR_ORDER);
        Entity order = dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_ORDER).get(sourceId);
        FormComponent avgLaborCostCalcForOrderForm = (FormComponent) view.getComponentByReference("form");
        Entity avgLaborCostCalcForOrder = avgLaborCostCalcForOrderDD.find().add(SearchRestrictions.belongsTo("order", order))
                .uniqueResult();
        if (avgLaborCostCalcForOrder == null) {
            avgLaborCostCalcForOrder = createAverageWithDataFromOrder(order, avgLaborCostCalcForOrderDD);
        }
        avgLaborCostCalcForOrderForm.setEntity(avgLaborCostCalcForOrder);
        avgLaborCostCalcForOrderForm.performEvent(view, "refresh", args);
    }

    private Entity createAverageWithDataFromOrder(final Entity order, final DataDefinition avgLaborCostCalcForOrderDD) {
        Entity avgLaborCostCalcForOrder = avgLaborCostCalcForOrderDD.create();
        avgLaborCostCalcForOrder.setField(ORDER, order);
        avgLaborCostCalcForOrder.setField(START_DATE, order.getField(OrderFields.START_DATE));
        avgLaborCostCalcForOrder.setField(FINISH_DATE, order.getField(OrderFields.FINISH_DATE));
        avgLaborCostCalcForOrder.setField(PRODUCTION_LINE, order.getBelongsToField(OrderFields.PRODUCTION_LINE));
        avgLaborCostCalcForOrder.setField(BASED_ON, "01assignment");
        return avgLaborCostCalcForOrder.getDataDefinition().save(avgLaborCostCalcForOrder);

    }

    public void generateAssignmentWorkerToShiftAndAverageCost(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        state.performEvent(view, "save", args);
        FieldComponent startDate = (FieldComponent) view.getComponentByReference(START_DATE);
        FieldComponent finishDate = (FieldComponent) view.getComponentByReference(FINISH_DATE);
        FieldComponent averageLaborHourlyCost = (FieldComponent) view.getComponentByReference(AVERAGE_LABOR_HOURLY_COST);

        FormComponent form = (FormComponent) view.getComponentByReference("form");
        Entity avgLaborCostCalcForOrder = form.getEntity().getDataDefinition().get(form.getEntityId());

        LookupComponent lookup = (LookupComponent) view.getComponentByReference("productionLine");
        Entity productionLine = lookup.getEntity();

        Date start = DateUtils.parseDate(startDate.getFieldValue());
        Date finish = DateUtils.parseDate(finishDate.getFieldValue());

        avgLaborCostCalcForOrder = averageCostService.generateAssignmentWorkerToShiftAndAverageCost(avgLaborCostCalcForOrder,
                start, finish, productionLine);
        form.setEntity(avgLaborCostCalcForOrder);
        state.performEvent(view, "save", args);
        // TODO ALBR why refresh actions on field or form doesn't work?
        averageLaborHourlyCost.setFieldValue(avgLaborCostCalcForOrder.getDecimalField(AVERAGE_LABOR_HOURLY_COST));
        averageLaborHourlyCost.requestComponentUpdateState();
    }

    public void copyToOperationsNorms(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent form = (FormComponent) view.getComponentByReference("form");
        Entity avgLaborCostCalcForOrder = form.getEntity().getDataDefinition().get(form.getEntityId());
        Entity order = avgLaborCostCalcForOrder.getBelongsToField(AvgLaborCostCalcForOrderFields.ORDER);
        List<Entity> tocs = order.getBelongsToField(OrderFields.TECHNOLOGY)
                .getHasManyField(TechnologyFields.OPERATION_COMPONENTS);
        for (Entity toc : tocs) {
            toc.setField(LABOR_HOURLY_COST, avgLaborCostCalcForOrder.getDecimalField(AVERAGE_LABOR_HOURLY_COST));
            toc.getDataDefinition().save(toc);
        }
    }

}
