package market.properties;

import org.springframework.beans.factory.annotation.Value;

public class PaginationProperties {

	private int backendProduct;

	public PaginationProperties() {
	}

	public PaginationProperties(@Value("${pagination.backend.product}") int backendProduct) {
		this.backendProduct = backendProduct;
	}

	public int getBackendProduct() {
		return backendProduct;
	}
}