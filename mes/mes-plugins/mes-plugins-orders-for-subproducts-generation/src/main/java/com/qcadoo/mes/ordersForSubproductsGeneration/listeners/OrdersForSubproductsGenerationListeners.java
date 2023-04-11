/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
 * Version: 1.4
 * <p>
 * This file is part of Qcadoo.
 * <p>
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.ordersForSubproductsGeneration.listeners;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.materialRequirementCoverageForOrder.MaterialRequirementCoverageForOrderService;
import com.qcadoo.mes.materialRequirementCoverageForOrder.constans.CoverageForOrderFields;
import com.qcadoo.mes.orderSupplies.constants.MaterialRequirementCoverageFields;
import com.qcadoo.mes.orderSupplies.constants.OrderSuppliesConstants;
import com.qcadoo.mes.orderSupplies.coverage.MaterialRequirementCoverageService;
import com.qcadoo.mes.orderSupplies.register.RegisterService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.ordersForSubproductsGeneration.OrdersForSubproductsGenerationService;
import com.qcadoo.mes.ordersForSubproductsGeneration.constants.CoverageForOrderFieldsOFSPG;
import com.qcadoo.mes.ordersForSubproductsGeneration.constants.OrderFieldsOFSPG;
import com.qcadoo.mes.ordersForSubproductsGeneration.constants.SubOrdersFields;
import com.qcadoo.mes.technologies.constants.ProductStructureTreeNodeFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.JoinType;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;

@Service
public class OrdersForSubproductsGenerationListeners {

    protected static final Logger LOG = LoggerFactory.getLogger(OrdersForSubproductsGenerationListeners.class);

    private static final String L_FORM = "form";

    private static final String L_ORDERS_GROUP = "ordersGroup";

    private static final String L_ORDERS = "orders";

    @Autowired
    private OrdersForSubproductsGenerationService ordersForSubproductsGenerationService;

    @Autowired
    private MaterialRequirementCoverageService materialRequirementCoverageService;

    @Autowired
    private MaterialRequirementCoverageForOrderService forOrderService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public final void generateSimpleOrdersForSubProducts(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        FormComponent subOrdersForm = (FormComponent) view.getComponentByReference(L_FORM);

        Entity subOrders = subOrdersForm.getEntity();
        Entity subOrdersFromDB = subOrders.getDataDefinition().get(subOrders.getId());

        try {
            fillGenerationProgressFlag(subOrdersFromDB, SubOrdersFields.ORDER_GENERATION_IN_PROGRESS, true);
            simpleGenerate(view, state, subOrdersFromDB);
        } catch (Exception ex) {
            LOG.error("Error when generation orders for components: ", ex);
            view.addMessage("qcadooView.errorPage.error.internalError.explanation", ComponentState.MessageType.FAILURE);
        } finally {
            fillGenerationProgressFlag(subOrdersFromDB, SubOrdersFields.ORDER_GENERATION_IN_PROGRESS, false);
        }
    }

    private final void simpleGenerate(final ViewDefinitionState view, final ComponentState state, final Entity subOrders) {
        LOG.info(String.format("Start generation orders for components. Sub orders: %d", subOrders.getId()));

        Integer generatedOrders = 0;

        List<Entity> orders = Lists.newArrayList();

        Entity subOrdersOrdersGroup = subOrders.getBelongsToField(L_ORDERS_GROUP);
        Entity subOrdersOrder = subOrders.getBelongsToField(SubOrdersFields.ORDER);

        if (Objects.nonNull(subOrdersOrdersGroup)) {
            orders = subOrdersOrdersGroup.getHasManyField(L_ORDERS);
        } else if (Objects.nonNull(subOrdersOrder)) {
            orders.add(subOrdersOrder);
        }

        for (Entity order : orders) {
            showMessagesForNotAcceptedComponents(view, order);

            List<Entity> registryEntries = registerService.findComponentRegistryEntries(order);

			generatedOrders = generateSimpleOrders(registryEntries, order, generatedOrders);

            if (!registryEntries.isEmpty()) {
                subOrders.setField(SubOrdersFields.GENERATED_ORDERS, true);

                subOrders.getDataDefinition().save(subOrders);
            }

            int index = 1;

            boolean generateSubOrdersForTree = true;

            while (generateSubOrdersForTree) {
                List<Entity> subOrdersForActualLevel = ordersForSubproductsGenerationService.getSubOrdersForRootAndLevel(order,
                        index);

                if (subOrdersForActualLevel.isEmpty()) {
                    generateSubOrdersForTree = false;
                }

                for (Entity subOrderForActualLevel : subOrdersForActualLevel) {
                    registryEntries = registerService.findComponentRegistryEntries(subOrderForActualLevel);

					generatedOrders = generateSimpleOrders(registryEntries, subOrderForActualLevel, generatedOrders);
                }

                ++index;
            }
        }

        if (generatedOrders > 0) {
            state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.generatedMessageSuccess",
                    ComponentState.MessageType.SUCCESS, false, generatedOrders.toString());
        } else {
            state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.generatedMessageSuccessNoOrders",
                    ComponentState.MessageType.SUCCESS, false);
        }

        LOG.info(String.format("Finish generation orders for components. Sub orders: %d", subOrders.getId()));
    }

    private Integer generateSimpleOrders(final List<Entity> registryEntries, final Entity order,
            Integer generatedOrders) {
        int index = 1;

        for (Entity registryEntry : registryEntries) {
            ordersForSubproductsGenerationService.generateSimpleOrderForSubProduct(registryEntry, order,
                    LocaleContextHolder.getLocale(), index);

            ++index;
            ++generatedOrders;
        }

        return generatedOrders;
    }

    private void showMessagesForNotAcceptedComponents(final ViewDefinitionState view, final Entity order) {
        List<Entity> nodes = ordersForSubproductsGenerationService.getProductNodesWithCheckedTechnologies(view, order);

        if (!nodes.isEmpty()) {
            String componentsWithCheckedTechnology = nodes.stream().map(
                    node -> node.getBelongsToField(ProductStructureTreeNodeFields.PRODUCT).getStringField(ProductFields.NUMBER))
                    .collect(Collectors.joining(", "));

            view.addMessage("ordersForSubproductsGeneration.ordersForSubproducts.generate.componentsWithCheckedTechnologies",
                    ComponentState.MessageType.INFO, false, componentsWithCheckedTechnology);
        }
    }

    public final void generateOrdersForSubProducts(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        Optional<Entity> optionalMrc = getGeneratingMRC();

        if (optionalMrc.isPresent()) {
            Entity mrc = optionalMrc.get();

            if (mrc.getBelongsToField(CoverageForOrderFields.ORDER) == null) {
                state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.generationInProgressSimple",
                        ComponentState.MessageType.INFO, false);
            } else {
                state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.generationInProgress",
                        ComponentState.MessageType.INFO, false,
                        mrc.getBelongsToField(CoverageForOrderFields.ORDER).getStringField(OrderFields.NUMBER));
            }
        } else {
			FormComponent materialRequirementForm = (FormComponent) view.getComponentByReference(L_FORM);

			Entity materialRequirement = materialRequirementForm.getEntity();
			Entity materialRequirementFromDB = materialRequirement.getDataDefinition().get(materialRequirement.getId());

            try {
                if (hasAlreadyGeneratedOrders(view)) {
                    state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.ordersAlreadyGenerated",
                            ComponentState.MessageType.INFO, false);

                    return;
                }

                fillGenerationProgressFlag(materialRequirementFromDB, MaterialRequirementCoverageFields.ORDER_GENERATION_IN_PROGRESS, true);
                generate(view, state, materialRequirementFromDB);
            } catch (Exception ex) {
                LOG.error("Error when generation orders for components: ", ex);

                view.addMessage("qcadooView.errorPage.error.internalError.explanation", ComponentState.MessageType.FAILURE);
            } finally {
                fillGenerationProgressFlag(materialRequirementFromDB, MaterialRequirementCoverageFields.ORDER_GENERATION_IN_PROGRESS, false);
            }
        }
    }

    private Optional<Entity> getGeneratingMRC() {
        SearchCriteriaBuilder searchCriteriaBuilder = getMaterialRequirementCoverageDD().find();

        searchCriteriaBuilder.add(SearchRestrictions.eq(MaterialRequirementCoverageFields.ORDER_GENERATION_IN_PROGRESS, true));

        List<Entity> entities = searchCriteriaBuilder.list().getEntities();

        if (entities.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(entities.get(0));
        }
    }

    private boolean hasAlreadyGeneratedOrders(final ViewDefinitionState view) {
        FormComponent coverageForm = (FormComponent) view.getComponentByReference(L_FORM);

        Entity coverageEntity = coverageForm.getPersistedEntityWithIncludedFormValues();
        Entity order = coverageEntity.getBelongsToField(CoverageForOrderFields.ORDER);
        List<Entity> coverageOrders = coverageEntity.getHasManyField(MaterialRequirementCoverageFields.COVERAGE_ORDERS);

        if (order != null) {
            List<Entity> entities = orderDD().find()
                    .add(SearchRestrictions.belongsTo(OrderFieldsOFSPG.ROOT, OrdersConstants.PLUGIN_IDENTIFIER,
                            OrdersConstants.MODEL_ORDER, order.getId()))
                    .add(SearchRestrictions.isNotNull(OrderFieldsOFSPG.PARENT)).list().getEntities();

            if (!entities.isEmpty()) {
                return true;
            }
        } else if (!coverageOrders.isEmpty()) {
            List<Long> ids = coverageOrders.stream().map(co -> co.getId()).collect(Collectors.toList());
            List<Entity> entities = orderDD().find().createAlias(OrderFieldsOFSPG.ROOT, OrderFieldsOFSPG.ROOT, JoinType.LEFT)
                    .add(SearchRestrictions.in(OrderFieldsOFSPG.ROOT + ".id", Lists.newArrayList(ids)))
                    .add(SearchRestrictions.isNotNull(OrderFieldsOFSPG.PARENT)).list().getEntities();

            if (!entities.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private final void generate(final ViewDefinitionState view, final ComponentState state, final Entity materialRequirementCoverage) {
        LOG.info(String.format("Start generation orders for components. Material requirement coverage: %d",
				materialRequirementCoverage.getId()));

        Integer generatedOrders = 0;

        if (materialRequirementCoverage.getId() != null) {
            List<Entity> orders = materialRequirementCoverage.getHasManyField(MaterialRequirementCoverageFields.COVERAGE_ORDERS);

            for (Entity order : orders) {
                showMessagesForNotAcceptedComponents(view, order);

                List<Entity> products = ordersForSubproductsGenerationService.getComponentProducts(materialRequirementCoverage, order);

				generatedOrders = generateOrders(products, order, generatedOrders);

                int index;

                if (!products.isEmpty()) {
					materialRequirementCoverage.setField(CoverageForOrderFieldsOFSPG.GENERATED_ORDERS, true);

					materialRequirementCoverage.getDataDefinition().save(materialRequirementCoverage);
                }

                index = 1;

                boolean generateSubOrdersForTree = true;

                while (generateSubOrdersForTree) {
                    List<Entity> subOrdersForActualLevel = ordersForSubproductsGenerationService
                            .getSubOrdersForRootAndLevel(order, index);

                    if (subOrdersForActualLevel.isEmpty()) {
                        generateSubOrdersForTree = false;
                    }

                    for (Entity subOrderForActualLevel : subOrdersForActualLevel) {
                        Optional<Entity> mayBeMaterialRequirementCoverage = forOrderService.createMRCFO(subOrderForActualLevel,
								materialRequirementCoverage);

                        if (mayBeMaterialRequirementCoverage.isPresent()) {
                            Entity materialRequirementCoverageForSubOrder = mayBeMaterialRequirementCoverage.get();

                            materialRequirementCoverageService.estimateProductCoverageInTime(materialRequirementCoverageForSubOrder);

                            products = ordersForSubproductsGenerationService
                                    .getCoverageProductsForOrder(materialRequirementCoverageForSubOrder, subOrderForActualLevel);

                            generatedOrders = generateOrders(products, subOrderForActualLevel, generatedOrders);
                        } else {
                            state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.coverageErrors",
                                    ComponentState.MessageType.FAILURE, false);

                            return;
                        }
                    }

                    ++index;
                }
            }
        }

        if (generatedOrders > 0) {
            state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.generatedMessageSuccess",
                    ComponentState.MessageType.SUCCESS, false, generatedOrders.toString());
        } else {
            state.addMessage("ordersForSubproductsGeneration.generationSubOrdersAction.generatedMessageSuccessNoOrders",
                    ComponentState.MessageType.SUCCESS, false);
        }

        LOG.info(String.format("Finish generation orders for components. Material requirement coverage: %d",
                materialRequirementCoverage.getId()));
    }

    private Integer generateOrders(final List<Entity> products, final Entity order, Integer generatedOrders) {
        int index = 1;

        for (Entity product : products) {
            ordersForSubproductsGenerationService.generateOrderForSubProduct(product, order, LocaleContextHolder.getLocale(),
                    index);

            ++index;
            ++generatedOrders;
        }

        return generatedOrders;
    }

	private void fillGenerationProgressFlag(final Entity entity, final String fieldName, final boolean orderGenerationInProgress) {
		entity.setField(fieldName, orderGenerationInProgress);

		entity.getDataDefinition().fastSave(entity);
	}

    private DataDefinition orderDD() {
        return dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_ORDER);
    }

    private DataDefinition getMaterialRequirementCoverageDD() {
        return dataDefinitionService.get(OrderSuppliesConstants.PLUGIN_IDENTIFIER,
                OrderSuppliesConstants.MODEL_MATERIAL_REQUIREMENT_COVERAGE);
    }

}
