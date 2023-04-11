package com.qcadoo.mes.costCalculation.print;

import com.qcadoo.model.api.Entity;

import java.math.BigDecimal;
import java.util.Objects;

public class ComponentsCalculationHolder {

    private final Entity toc;
    private final Entity product;
    private BigDecimal materialCost;
    private BigDecimal laborCost;
    private BigDecimal sumOfCost;
    private BigDecimal costPerUnit;

    public ComponentsCalculationHolder(final Entity toc, final Entity product) {
        this.toc = toc;
        this.product = product;
    }

    public Entity getToc() {
        return toc;
    }

    public Entity getProduct() {
        return product;
    }

    public BigDecimal getMaterialCost() {
        return materialCost;
    }

    public void setMaterialCost(BigDecimal materialCost) {
        this.materialCost = materialCost;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public void setLaborCost(BigDecimal laborCost) {
        this.laborCost = laborCost;
    }

    public BigDecimal getSumOfCost() {
        return sumOfCost;
    }

    public void setSumOfCost(BigDecimal sumOfCost) {
        this.sumOfCost = sumOfCost;
    }

    public BigDecimal getCostPerUnit() {
        return costPerUnit;
    }

    public void setCostPerUnit(BigDecimal costPerUnit) {
        this.costPerUnit = costPerUnit;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ComponentsCalculationHolder))
            return false;
        ComponentsCalculationHolder that = (ComponentsCalculationHolder) o;
        return Objects.equals(toc.getId(), that.toc.getId()) && Objects.equals(product.getId(), that.product.getId());
    }

    @Override public int hashCode() {
        return Objects.hash(toc.getId(), product.getId());
    }
}
