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
package com.qcadoo.mes.orders.hooks;

import com.google.common.collect.Lists;
import com.qcadoo.mes.orders.OrderService;
import com.qcadoo.mes.orders.constants.ParameterFieldsO;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.security.api.SecurityService;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.CheckBoxComponent;
import com.qcadoo.view.api.components.GridComponent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.DELAYED_EFFECTIVE_DATE_FROM_TIME;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.DELAYED_EFFECTIVE_DATE_TO_TIME;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.EARLIER_EFFECTIVE_DATE_FROM_TIME;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.EARLIER_EFFECTIVE_DATE_TO_TIME;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.REASON_NEEDED_WHEN_DELAYED_EFFECTIVE_DATE_FROM;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.REASON_NEEDED_WHEN_DELAYED_EFFECTIVE_DATE_TO;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.REASON_NEEDED_WHEN_EARLIER_EFFECTIVE_DATE_FROM;
import static com.qcadoo.mes.orders.constants.ParameterFieldsO.REASON_NEEDED_WHEN_EARLIER_EFFECTIVE_DATE_TO;

@Service
public class ParametersHooksO {

    private static final String L_REALIZATION_FROM_STOCK = "realizationFromStock";

    private static final String L_ALWAYS_ORDER_ITEMS_WITH_PERSONALIZATION = "alwaysOrderItemsWithPersonalization";

    private static final String L_REALIZATION_LOCATIONS = "realizationLocations";

    @Autowired
    private OrderService orderService;

    @Autowired
    private SecurityService securityService;

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    public void onSave(final DataDefinition parameterDD, final Entity parameter) {
        if (!parameter.getBooleanField(ParameterFieldsO.REALIZATION_FROM_STOCK)) {
            parameter.setField(ParameterFieldsO.REALIZATION_LOCATIONS, Lists.newArrayList());
        }
    }

    public boolean validatesWith(final DataDefinition parameterDD, final Entity parameter) {
        boolean isValid = true;
        if (parameter.getBooleanField(ParameterFieldsO.REALIZATION_FROM_STOCK)
                && parameter.getHasManyField(ParameterFieldsO.REALIZATION_LOCATIONS).isEmpty()) {
            parameter.addGlobalError("orders.ordersParameters.window.mainTab.ordersParameters.realizationLocations.error.empty",
                    Boolean.FALSE);
            isValid = false;
        }
        return isValid;
    }

    public void onOrdersParameterBeforeRender(final ViewDefinitionState view) {
        CheckBoxComponent realizationFromStockComponent = (CheckBoxComponent) view
                .getComponentByReference(L_REALIZATION_FROM_STOCK);
        CheckBoxComponent alwaysOrderItemsWithPersonalizationComponent = (CheckBoxComponent) view
                .getComponentByReference(L_ALWAYS_ORDER_ITEMS_WITH_PERSONALIZATION);
        GridComponent realizationLocationsGrid = (GridComponent) view.getComponentByReference(L_REALIZATION_LOCATIONS);
        if (realizationFromStockComponent.isChecked()) {
            alwaysOrderItemsWithPersonalizationComponent.setEnabled(true);
            realizationLocationsGrid.setEditable(true);
        } else {
            alwaysOrderItemsWithPersonalizationComponent.setEnabled(false);
            realizationLocationsGrid.setEditable(false);
        }
        alwaysOrderItemsWithPersonalizationComponent.requestComponentUpdateState();
    }

    public void onBeforeRender(final ViewDefinitionState view) {
        showTimeFields(view);
        hideTabs(view);
        CheckBoxComponent realizationFromStockComponent = (CheckBoxComponent) view
                .getComponentByReference(L_REALIZATION_FROM_STOCK);
        CheckBoxComponent alwaysOrderItemsWithPersonalizationComponent = (CheckBoxComponent) view
                .getComponentByReference(L_ALWAYS_ORDER_ITEMS_WITH_PERSONALIZATION);
        GridComponent realizationLocationsGrid = (GridComponent) view.getComponentByReference(L_REALIZATION_LOCATIONS);
        if (realizationFromStockComponent.isChecked()) {
            alwaysOrderItemsWithPersonalizationComponent.setEnabled(true);
            realizationLocationsGrid.setEditable(true);
        } else {
            alwaysOrderItemsWithPersonalizationComponent.setEnabled(false);
            realizationLocationsGrid.setEditable(false);
        }
        alwaysOrderItemsWithPersonalizationComponent.requestComponentUpdateState();
    }

    public void showTimeFields(final ViewDefinitionState view) {
        orderService.changeFieldState(view, REASON_NEEDED_WHEN_DELAYED_EFFECTIVE_DATE_FROM, DELAYED_EFFECTIVE_DATE_FROM_TIME);
        orderService.changeFieldState(view, REASON_NEEDED_WHEN_EARLIER_EFFECTIVE_DATE_FROM, EARLIER_EFFECTIVE_DATE_FROM_TIME);
        orderService.changeFieldState(view, REASON_NEEDED_WHEN_DELAYED_EFFECTIVE_DATE_TO, DELAYED_EFFECTIVE_DATE_TO_TIME);
        orderService.changeFieldState(view, REASON_NEEDED_WHEN_EARLIER_EFFECTIVE_DATE_TO, EARLIER_EFFECTIVE_DATE_TO_TIME);
    }

    public void hideTabs(final ViewDefinitionState view) {
        ComponentState pktTab = view.getComponentByReference("pktTab");
        if (!securityService.hasCurrentUserRole(ROLE_SUPERADMIN)) {
            pktTab.setVisible(false);
        }
    }

}
