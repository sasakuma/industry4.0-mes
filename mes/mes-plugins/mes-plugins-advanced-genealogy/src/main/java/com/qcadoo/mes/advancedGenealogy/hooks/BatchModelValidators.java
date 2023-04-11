/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
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
package com.qcadoo.mes.advancedGenealogy.hooks;

import static com.qcadoo.model.api.search.SearchProjections.alias;
import static com.qcadoo.model.api.search.SearchProjections.id;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.advancedGenealogy.constants.BatchFields;
import com.qcadoo.mes.advancedGenealogy.constants.BatchNumberUniqueness;
import com.qcadoo.mes.advancedGenealogy.constants.ParameterFieldsAG;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchCriterion;

@Service
public class BatchModelValidators {

    private static final String ERROR_MESSAGE_TEMPLATE = "advancedGenealogy.batch.message.batchNumberNotUnique.%s";

    @Autowired
    private ParameterService parameterService;

    public final boolean checkIfBatchNumberIsUnique(final DataDefinition batchDD, final Entity batch) {
        BatchNumberUniqueness batchNumberUniqueness = getBatchNumberUniqueness();

        if (batchNumberUniqueness == null) {
            return true;
        }

        SearchCriterion criterion = batchNumberUniqueness.buildCriterionFor(batch);

        if (existsAnyBatchMatchingCriterion(batchDD, criterion)) {
            String errorMessage = String.format(ERROR_MESSAGE_TEMPLATE, batchNumberUniqueness.getStringValue());
            batch.addError(batchDD.getField(BatchFields.NUMBER), errorMessage);

            return false;
        }

        return true;
    }

    private boolean existsAnyBatchMatchingCriterion(final DataDefinition batchDD, final SearchCriterion criterion) {
        SearchCriteriaBuilder scb = batchDD.find();
        scb.add(criterion);
        // to decrease mapping overhead
        scb.setProjection(alias(id(), "id"));

        return scb.setMaxResults(1).uniqueResult() != null;
    }

    private BatchNumberUniqueness getBatchNumberUniqueness() {
        Entity parameter = parameterService.getParameter();
        
        String batchNumberUniquenessValue = parameter.getStringField(ParameterFieldsAG.BATCH_NUMBER_UNIQUENESS);

        return BatchNumberUniqueness.parseString(batchNumberUniquenessValue);
    }

}
