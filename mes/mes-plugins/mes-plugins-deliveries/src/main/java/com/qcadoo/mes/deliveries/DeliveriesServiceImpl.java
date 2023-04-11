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
package com.qcadoo.mes.deliveries;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.CompanyService;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.basic.ProductService;
import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.basic.constants.CompanyFields;
import com.qcadoo.mes.basic.constants.CurrencyFields;
import com.qcadoo.mes.basic.constants.ProductFamilyElementType;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.util.CurrencyService;
import com.qcadoo.mes.deliveries.constants.ColumnForDeliveriesFields;
import com.qcadoo.mes.deliveries.constants.ColumnForOrdersFields;
import com.qcadoo.mes.deliveries.constants.CompanyProductFields;
import com.qcadoo.mes.deliveries.constants.DefaultAddressType;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveriesConstants;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.mes.deliveries.constants.ParameterFieldsD;
import com.qcadoo.mes.deliveries.print.DeliveryProduct;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchOrders;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.WindowComponent;
import com.qcadoo.view.api.ribbon.RibbonActionItem;
import com.qcadoo.view.api.ribbon.RibbonGroup;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class DeliveriesServiceImpl implements DeliveriesService {

    private static final String L_FORM = "form";

    private static final String L_WINDOW = "window";

    private static final String L_PRODUCT = "product";

    private static final String L_SHOW_PRODUCT = "showProduct";

    private static final String L_PRICE_PER_UNIT = "pricePerUnit";

    private static final String L_TOTAL_PRICE = "totalPrice";

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private CurrencyService currencyService;

    @Autowired
    private ProductService productService;

    @Override
    public Entity getDelivery(final Long deliveryId) {
        return getDeliveryDD().get(deliveryId);
    }

    @Override
    public Entity getOrderedProduct(final Long orderedProductId) {
        return getOrderedProductDD().get(orderedProductId);
    }

    @Override
    public Entity getDeliveredProduct(final Long deliveredProductId) {
        return getDeliveredProductDD().get(deliveredProductId);
    }

    @Override
    public Entity getCompanyProduct(final Long companyProductId) {
        return getCompanyProductDD().get(companyProductId);
    }

    @Override
    public Entity getCompanyProductsFamily(final Long companyProductsFamilyId) {
        return getCompanyProductsFamilyDD().get(companyProductsFamilyId);
    }

    @Override
    public List<Entity> getColumnsForDeliveries() {
        List<Entity> columnsForDeliveries = getColumnForDeliveriesDD().find()
                .addOrder(SearchOrders.asc(ColumnForDeliveriesFields.SUCCESSION)).list().getEntities();
        List<Entity> deliveriesColumn = new ArrayList<Entity>();
        Entity successionColumn = getColumnForDeliveriesDD().find()
                .add(SearchRestrictions.eq(ColumnForDeliveriesFields.IDENTIFIER, "succession")).uniqueResult();
        deliveriesColumn.add(successionColumn);
        for (Entity entity : columnsForDeliveries) {
            if (!entity.getStringField(ColumnForDeliveriesFields.IDENTIFIER).equals(
                    successionColumn.getStringField(ColumnForDeliveriesFields.IDENTIFIER))) {
                deliveriesColumn.add(entity);
            }
        }
        return deliveriesColumn;
    }

    @Override
    public List<Entity> getColumnsForOrders() {
        List<Entity> columns = new LinkedList<Entity>();
        List<Entity> columnComponents = getColumnForOrdersDD().find()
                .addOrder(SearchOrders.asc(ColumnForOrdersFields.SUCCESSION)).list().getEntities();

        for (Entity columnComponent : columnComponents) {
            Entity columnDefinition = columnComponent.getBelongsToField("columnForOrders");

            columns.add(columnDefinition);
        }

        return columns;
    }

    @Override
    public DataDefinition getDeliveryDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_DELIVERY);
    }

    public DataDefinition getProductDD() {
        return dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_PRODUCT);
    }

    @Override
    public DataDefinition getOrderedProductDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_ORDERED_PRODUCT);
    }

    @Override
    public DataDefinition getDeliveredProductDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_DELIVERED_PRODUCT);
    }

    @Override
    public DataDefinition getCompanyProductDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_COMPANY_PRODUCT);
    }

    @Override
    public DataDefinition getCompanyProductsFamilyDD() {
        return dataDefinitionService
                .get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_COMPANY_PRODUCTS_FAMILY);
    }

    @Override
    public DataDefinition getColumnForDeliveriesDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_COLUMN_FOR_DELIVERIES);
    }

    @Override
    public DataDefinition getColumnForOrdersDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER,
                DeliveriesConstants.MODEL_PARAMETER_DELIVERY_ORDER_COLUMN);
    }

    @Override
    public Entity getProduct(final DeliveryProduct deliveryProduct) {
        if (deliveryProduct.getOrderedProductId() == null) {
            return getDeliveredProduct(deliveryProduct.getDeliveredProductId()).getBelongsToField(DeliveredProductFields.PRODUCT);
        } else {
            return getOrderedProduct(deliveryProduct.getOrderedProductId()).getBelongsToField(OrderedProductFields.PRODUCT);
        }
    }

    @Override
    public String getDescriptionDefaultValue() {
        Entity parameter = parameterService.getParameter();

        return parameter.getStringField(ParameterFieldsD.DEFAULT_DESCRIPTION);
    }

    @Override
    public String getDeliveryAddressDefaultValue() {
        Entity parameter = parameterService.getParameter();

        if (DefaultAddressType.OTHER.getStringValue().equals(parameter.getStringField(ParameterFieldsD.DEFAULT_ADDRESS))) {
            return parameter.getStringField(ParameterFieldsD.OTHER_ADDRESS);
        } else {
            return generateAddressFromCompany(companyService.getCompany());
        }
    }

    @Override
    public String generateAddressFromCompany(final Entity company) {
        StringBuilder address = new StringBuilder();

        if (company != null) {
            String street = company.getStringField(CompanyFields.STREET);
            String house = company.getStringField(CompanyFields.HOUSE);
            String flat = company.getStringField(CompanyFields.FLAT);
            String zipCode = company.getStringField(CompanyFields.ZIP_CODE);
            String city = company.getStringField(CompanyFields.CITY);

            if (StringUtils.isNotEmpty(street)) {
                address.append(street);
                if (StringUtils.isNotEmpty(house)) {
                    address.append(" ");
                    address.append(house);
                    if (StringUtils.isNotEmpty(flat)) {
                        address.append("/");
                        address.append(flat);
                    }
                }
                if (StringUtils.isNotEmpty(city)) {
                    address.append(", ");
                }
            }
            if (StringUtils.isNotEmpty(city)) {
                if (StringUtils.isNotEmpty(zipCode)) {
                    address.append(zipCode);
                    address.append(" ");
                }
                address.append(city);
            }
        }

        return address.toString();
    }

    @Override
    public void fillUnitFields(final ViewDefinitionState view, final String productName, final List<String> referenceNames) {
        Entity product = getProductEntityByComponentName(view, productName);
        fillUnitFields(view, product, referenceNames);
    }

    public void fillUnitFields(final ViewDefinitionState view, final Entity product, final List<String> referenceNames) {
        String unit = "";

        if (product != null) {
            unit = product.getStringField(ProductFields.UNIT);
        }

        for (String referenceName : referenceNames) {
            FieldComponent field = (FieldComponent) view.getComponentByReference(referenceName);
            field.setFieldValue(unit);
            field.requestComponentUpdateState();
        }
    }

    @Override
    public void fillUnitFields(final ViewDefinitionState view, final Entity product, final List<String> referenceNames,
            final List<String> additionalUnitNames) {
        fillUnitFields(view, product, referenceNames);
        String additionalUnit = "";

        if (product != null) {
            additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);
            if (additionalUnit == null) {
                additionalUnit = product.getStringField(ProductFields.UNIT);
            }
        }

        for (String additionalUnitName : additionalUnitNames) {
            FieldComponent field = (FieldComponent) view.getComponentByReference(additionalUnitName);
            field.setFieldValue(additionalUnit);
            field.requestComponentUpdateState();
        }
    }

    @Override
    public void fillUnitFields(final ViewDefinitionState view, final String productName, final List<String> referenceNames,
            final List<String> additionalUnitNames) {
        Entity product = getProductEntityByComponentName(view, productName);

        fillUnitFields(view, product, referenceNames, additionalUnitNames);
    }

    @Override
    public void fillCurrencyFields(final ViewDefinitionState view, final List<String> referenceNames) {
        String currency = currencyService.getCurrencyAlphabeticCode();

        if (StringUtils.isEmpty(currency)) {
            return;
        }

        for (String reference : referenceNames) {
            FieldComponent field = (FieldComponent) view.getComponentByReference(reference);
            field.setFieldValue(currency);
            field.requestComponentUpdateState();
        }
    }

    @Override
    public void fillCurrencyFieldsForDelivery(final ViewDefinitionState view, final List<String> referenceNames,
            final Entity delivery) {
        String currency = getCurrency(delivery);

        if (currency == null) {
            return;
        }

        for (String reference : referenceNames) {
            FieldComponent field = (FieldComponent) view.getComponentByReference(reference);
            field.setFieldValue(currency);
            field.requestComponentUpdateState();
        }
    }

    @Override
    public String getCurrency(final Entity delivery) {
        if (delivery == null) {
            return "";
        }

        Entity currency = delivery.getBelongsToField(DeliveryFields.CURRENCY);

        if (currency == null) {
            return currencyService.getCurrencyAlphabeticCode();
        } else {
            return currency.getDataDefinition().get(currency.getId()).getStringField(CurrencyFields.ALPHABETIC_CODE);
        }
    }

    @Override
    public void recalculatePriceFromTotalPrice(final ViewDefinitionState view, final String quantityFieldReference) {
        if (!isValidDecimalField(view, Arrays.asList(L_PRICE_PER_UNIT, L_TOTAL_PRICE, quantityFieldReference))) {
            return;
        }

        FieldComponent quantityField = (FieldComponent) view.getComponentByReference(quantityFieldReference);
        FieldComponent totalPriceField = (FieldComponent) view.getComponentByReference(L_TOTAL_PRICE);

        if (StringUtils.isNotEmpty((String) quantityField.getFieldValue())
                && StringUtils.isNotEmpty((String) totalPriceField.getFieldValue())) {
            calculatePriceUsingTotalCost(view, quantityField, totalPriceField);
        }
    }

    private void calculatePriceUsingTotalCost(final ViewDefinitionState view, FieldComponent quantityField,
            FieldComponent totalPriceField) {
        FieldComponent pricePerUnitField = (FieldComponent) view.getComponentByReference(L_PRICE_PER_UNIT);

        Locale locale = view.getLocale();

        BigDecimal quantity = getBigDecimalFromField(quantityField, locale);
        BigDecimal totalPrice = getBigDecimalFromField(totalPriceField, locale);

        BigDecimal pricePerUnit = numberService.setScaleWithDefaultMathContext(totalPrice.divide(quantity, numberService.getMathContext()));

        pricePerUnitField.setFieldValue(numberService.format(pricePerUnit));
        pricePerUnitField.requestComponentUpdateState();
    }

    @Override
    public void recalculatePriceFromPricePerUnit(final ViewDefinitionState view, final String quantityFieldReference) {
        if (!isValidDecimalField(view, Arrays.asList(L_PRICE_PER_UNIT, L_TOTAL_PRICE, quantityFieldReference))) {
            return;
        }

        FieldComponent quantityField = (FieldComponent) view.getComponentByReference(quantityFieldReference);
        FieldComponent pricePerUnitField = (FieldComponent) view.getComponentByReference(L_PRICE_PER_UNIT);

        if (StringUtils.isNotEmpty((String) quantityField.getFieldValue())
                && StringUtils.isNotEmpty((String) pricePerUnitField.getFieldValue())) {
            calculatePriceUsingPricePerUnit(view, quantityField, pricePerUnitField);
        }
    }

    private void calculatePriceUsingPricePerUnit(final ViewDefinitionState view, FieldComponent quantityField,
            FieldComponent pricePerUnitField) {
        FieldComponent totalPriceField = (FieldComponent) view.getComponentByReference(L_TOTAL_PRICE);

        Locale locale = view.getLocale();

        BigDecimal pricePerUnit = getBigDecimalFromField(pricePerUnitField, locale);
        BigDecimal quantity = getBigDecimalFromField(quantityField, locale);

        BigDecimal totalPrice = numberService.setScaleWithDefaultMathContext(pricePerUnit.multiply(quantity, numberService.getMathContext()));

        totalPriceField.setFieldValue(numberService.format(totalPrice));
        totalPriceField.requestComponentUpdateState();
    }

    @Override
    public void recalculatePrice(final ViewDefinitionState view, final String quantityFieldReference) {
        if (!isValidDecimalField(view, Arrays.asList(L_PRICE_PER_UNIT, L_TOTAL_PRICE, quantityFieldReference))) {
            return;
        }

        FieldComponent quantityField = (FieldComponent) view.getComponentByReference(quantityFieldReference);
        FieldComponent pricePerUnitField = (FieldComponent) view.getComponentByReference(L_PRICE_PER_UNIT);
        FieldComponent totalPriceField = (FieldComponent) view.getComponentByReference(L_TOTAL_PRICE);

        if (StringUtils.isNotEmpty((String) quantityField.getFieldValue())
                && StringUtils.isNotEmpty((String) pricePerUnitField.getFieldValue())) {
            calculatePriceUsingPricePerUnit(view, quantityField, pricePerUnitField);
        } else if (StringUtils.isNotEmpty((String) quantityField.getFieldValue())
                && StringUtils.isNotEmpty((String) totalPriceField.getFieldValue())) {
            calculatePriceUsingTotalCost(view, quantityField, totalPriceField);
        }
    }

    @Override
    public BigDecimal getBigDecimalFromField(final FieldComponent fieldComponent, final Locale locale) {
        Object value = fieldComponent.getFieldValue();

        try {
            DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(locale);
            format.setParseBigDecimal(true);

            return new BigDecimal(format.parse(value.toString()).doubleValue());
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean isValidDecimalField(final ViewDefinitionState view, final List<String> fileds) {
        boolean isValid = true;

        FormComponent form = (FormComponent) view.getComponentByReference(L_FORM);
        Entity entity = form.getEntity();

        for (String field : fileds) {
            try {
                entity.getDecimalField(field);
            } catch (IllegalArgumentException e) {
                form.findFieldComponentByName(field).addMessage("qcadooView.validate.field.error.invalidNumericFormat",
                        MessageType.FAILURE);
                isValid = false;
            }
        }

        return isValid;
    }

    @Override
    public void calculatePricePerUnit(final Entity entity, final String quantityFieldName) {
        BigDecimal totalPrice = entity.getDecimalField(OrderedProductFields.TOTAL_PRICE);
        BigDecimal pricePerUnit = entity.getDecimalField(OrderedProductFields.PRICE_PER_UNIT);
        BigDecimal quantity = entity.getDecimalField(quantityFieldName);

        boolean save = true;

        if ((pricePerUnit != null && changedFieldValue(entity, pricePerUnit, OrderedProductFields.PRICE_PER_UNIT))
                || (pricePerUnit != null && totalPrice == null)) {
            totalPrice = numberService.setScaleWithDefaultMathContext(calculateTotalPrice(quantity, pricePerUnit));
        } else if ((totalPrice != null && changedFieldValue(entity, totalPrice, OrderedProductFields.TOTAL_PRICE))
                || (totalPrice != null && pricePerUnit == null)) {
            pricePerUnit = numberService.setScaleWithDefaultMathContext(calculatePricePerUnit(quantity, totalPrice));
        } else {
            save = false;
        }

        if (save) {
            entity.setField(L_PRICE_PER_UNIT, pricePerUnit);
            entity.setField(L_TOTAL_PRICE, totalPrice);
        }
    }

    private boolean changedFieldValue(final Entity entity, final BigDecimal fieldValue, final String reference) {
        if (entity.getId() == null) {
            return true;
        }

        Entity entityFromDB = entity.getDataDefinition().get(entity.getId());

        return entityFromDB.getDecimalField(reference) == null
                || !(fieldValue.compareTo(entityFromDB.getDecimalField(reference)) == 0);
    }

    private BigDecimal calculatePricePerUnit(final BigDecimal quantity, final BigDecimal totalPrice) {
        BigDecimal pricePerUnit = BigDecimal.ZERO;

        if ((quantity == null) || (BigDecimal.ZERO.compareTo(quantity) == 0)) {
            pricePerUnit = null;
        } else {
            pricePerUnit = totalPrice.divide(quantity, numberService.getMathContext());
        }

        return pricePerUnit;
    }

    private BigDecimal calculateTotalPrice(final BigDecimal quantity, final BigDecimal pricePerUnit) {
        BigDecimal totalPrice = BigDecimal.ZERO;

        if ((quantity == null) || (BigDecimal.ZERO.compareTo(quantity) == 0)) {
            totalPrice = BigDecimal.ZERO;
        } else {
            totalPrice = pricePerUnit.multiply(quantity, numberService.getMathContext());
        }

        return totalPrice;
    }

    @Override
    public List<Entity> getColumnsWithFilteredCurrencies(final List<Entity> columns) {
        List<Entity> filteredCurrencyColumn = Lists.newArrayList();

        if (checkIfContainsPriceColumns(columns)) {
            filteredCurrencyColumn.addAll(columns);
        } else {
            for (Entity column : columns) {
                String identifier = column.getStringField(ColumnForOrdersFields.IDENTIFIER);

                if (!"currency".equals(identifier)) {
                    filteredCurrencyColumn.add(column);
                }
            }
        }

        return filteredCurrencyColumn;
    }

    private boolean checkIfContainsPriceColumns(final List<Entity> columns) {
        boolean contains = false;

        for (Entity column : columns) {
            String identifier = column.getStringField(ColumnForOrdersFields.IDENTIFIER);

            if (L_PRICE_PER_UNIT.equals(identifier) || L_TOTAL_PRICE.equals(identifier)) {
                contains = true;
            }
        }

        return contains;
    }

    @Override
    public void disableShowProductButton(final ViewDefinitionState view) {
        GridComponent orderedProductGrid = (GridComponent) view.getComponentByReference(DeliveryFields.ORDERED_PRODUCTS);
        GridComponent deliveredProductsGrid = (GridComponent) view.getComponentByReference(DeliveryFields.DELIVERED_PRODUCTS);

        WindowComponent window = (WindowComponent) view.getComponentByReference(L_WINDOW);
        RibbonGroup product = (RibbonGroup) window.getRibbon().getGroupByName(L_PRODUCT);
        RibbonActionItem showProduct = (RibbonActionItem) product.getItemByName(L_SHOW_PRODUCT);

        int sizeOfSelectedEntitiesOrderedGrid = orderedProductGrid.getSelectedEntities().size();
        int sizeOfSelectedEntitiesDelivereGrid = deliveredProductsGrid.getSelectedEntities().size();
        if ((sizeOfSelectedEntitiesOrderedGrid == 1 && sizeOfSelectedEntitiesDelivereGrid == 0)
                || (sizeOfSelectedEntitiesOrderedGrid == 0 && sizeOfSelectedEntitiesDelivereGrid == 1)) {
            showProduct.setEnabled(true);
        } else {
            showProduct.setEnabled(false);
        }

        showProduct.requestUpdate(true);
        window.requestRibbonRender();
    }

    private Entity getProductEntityByComponentName(final ViewDefinitionState view, final String productName) {
        ComponentState productComponentState = view.getComponentByReference(productName);
        Entity product = null;
        if (productComponentState instanceof LookupComponent) {
            product = ((LookupComponent) productComponentState).getEntity();
        }

        return product;
    }

    public Optional<Entity> getDefaultSupplier(Long productId) {
        Entity product = dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_PRODUCT).get(productId);

        if (product != null
                && ProductFamilyElementType.PARTICULAR_PRODUCT.getStringValue().equals(
                        product.getStringField(ProductFields.ENTITY_TYPE))) {
            Entity defaultSupplier = getDefaultSupplierForProductsFamily(productId);
            if (defaultSupplier != null) {
                return Optional.of(defaultSupplier);
            } else {
                return Optional.ofNullable(getDefaultSupplierForParticularProduct(productId));
            }
        }
        return Optional.empty();
    }

    public Optional<Entity> getDefaultSupplierWithIntegration(Long productId) {
        Entity product = dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_PRODUCT).get(productId);

        if (product != null
                && ProductFamilyElementType.PARTICULAR_PRODUCT.getStringValue().equals(
                        product.getStringField(ProductFields.ENTITY_TYPE))) {
            Entity defaultSupplier = getDefaultSupplierForParticularProduct(productId);
            if (defaultSupplier != null) {
                return Optional.of(defaultSupplier.getBelongsToField(CompanyProductFields.COMPANY));
            } else {
                defaultSupplier = getDefaultSupplierForProductsFamily(productId);
                if (defaultSupplier != null) {
                    return Optional.of(defaultSupplier.getBelongsToField(CompanyProductFields.COMPANY));
                }
            }
        }

        return getIntegrationDefaultSupplier();
    }

    public List<Entity> getSuppliersWithIntegration(Long productId) {
        List<Entity> suppliers = getSuppliersForProductsFamily(productId);
        suppliers.addAll(getSuppliersForParticularProduct(productId));
        getIntegrationDefaultSupplier().ifPresent(suppliers::add);
        return suppliers;
    }

    private Entity getDefaultSupplierForProductsFamily(Long productId) {
        Entity product = getProductDD().get(productId);

        Entity productFamily = product.getBelongsToField(ProductFields.PARENT);
        if (productFamily != null) {
            Entity companyProduct = getDefaultCompanyProductFamilyEntity(productFamily.getId());
            if (companyProduct == null) {
                boolean notFind = true;
                while (notFind) {
                    productFamily = productFamily.getBelongsToField(ProductFields.PARENT);
                    if (productFamily == null) {
                        return null;
                    }
                    companyProduct = getDefaultCompanyProductFamilyEntity(productFamily.getId());
                    if (companyProduct != null) {
                        return companyProduct;
                    }
                }
            }
            return companyProduct;

        } else {
            return null;
        }
    }

    private Entity getDefaultCompanyProductFamilyEntity(Long productId) {
        String query = "select company from #deliveries_companyProductsFamily company WHERE company.product.id = :id"
                + " and company.isDefault = true";
        return getCompanyProductDD().find(query).setParameter("id", productId).setMaxResults(1).uniqueResult();
    }

    private Entity getDefaultCompanyProductEntity(Long productId) {
        String query = "select company from #deliveries_companyProductsFamily company, #basic_product product where product.parent.id = company.product.id and product.id = :id"
                + " and company.isDefault = true";
        return getCompanyProductDD().find(query).setParameter("id", productId).setMaxResults(1).uniqueResult();
    }

    private Entity getDefaultSupplierForParticularProduct(Long productId) {
        String query = "select company from #deliveries_companyProduct company where company.product.id = :id"
                + " and company.isDefault = true";
        return getCompanyProductDD().find(query).setParameter("id", productId).setMaxResults(1).uniqueResult();
    }

    private Optional<Entity> getIntegrationDefaultSupplier() {
        return Optional.ofNullable(parameterService.getParameter().getBelongsToField("companyName"));
    }

    private List<Entity> getSuppliersForProductsFamily(Long productId) {
        String query = "select company.company from #deliveries_companyProductsFamily company, #basic_product product where product.parent.id = company.product.id and product.id = :id";
        return getCompanyProductDD().find(query).setParameter("id", productId).list().getEntities();
    }

    private List<Entity> getSuppliersForParticularProduct(Long productId) {
        String query = "select company.company from #deliveries_companyProduct company where company.product.id = :id";
        return getCompanyProductDD().find(query).setParameter("id", productId).list().getEntities();
    }

    public List<Entity> getSelectedOrderedProducts(final GridComponent orderedProductGrid) {
        List<Entity> result = Lists.newArrayList();
        Set<Long> ids = orderedProductGrid.getSelectedEntitiesIds();
        if (ids != null && !ids.isEmpty()) {
            final SearchCriteriaBuilder searchCriteria = getOrderedProductDD().find();
            searchCriteria.add(SearchRestrictions.in("id", ids));
            result = searchCriteria.list().getEntities();
        }
        return result;
    }
}
