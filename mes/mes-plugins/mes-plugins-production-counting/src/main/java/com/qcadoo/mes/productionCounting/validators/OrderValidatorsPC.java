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
package com.qcadoo.mes.productionCounting.validators;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.productionCounting.constants.OrderFieldsPC;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;

@Service
public class OrderValidatorsPC {

    public boolean checkAutoCloseFlagForSynchornizedOrder(final DataDefinition orderDD, final Entity order) {
        if (order.getBooleanField(OrderFieldsPC.AUTO_CLOSE_ORDER)
                && StringUtils.hasText(order.getStringField(OrderFields.EXTERNAL_NUMBER))) {
            order.addError(orderDD.getField(OrderFieldsPC.AUTO_CLOSE_ORDER),
                    "productionCounting.order.externalNumber.cannotBeAutoClose");
            return false;
        }
        return true;
    }

}
