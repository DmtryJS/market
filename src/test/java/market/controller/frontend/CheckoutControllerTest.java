package market.controller.frontend;

import market.MarketProperties;
import market.domain.*;
import market.dto.assembler.ContactsDtoAssembler;
import market.dto.assembler.OrderDtoAssembler;
import market.dto.assembler.ProductDtoAssembler;
import market.dto.assembler.UserAccountDtoAssembler;
import market.service.CartService;
import market.service.ContactsService;
import market.service.OrderService;
import market.service.UserAccountService;
import market.util.FixturesFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import sun.security.acl.PrincipalImpl;

import java.security.Principal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CheckoutController.class)
public class CheckoutControllerTest {
	private final MarketProperties marketProperties = new MarketProperties(400);

	private final OrderDtoAssembler orderDtoAssembler = new OrderDtoAssembler();
	private final ContactsDtoAssembler contactsDtoAssembler = new ContactsDtoAssembler();
	private final UserAccountDtoAssembler accountDtoAssembler = new UserAccountDtoAssembler();
	private final ProductDtoAssembler productDtoAssembler = new ProductDtoAssembler();

	@MockBean
	private UserAccountService userAccountService;
	@MockBean
	private ContactsService contactsService;
	@MockBean
	private OrderService orderService;
	@MockBean
	private CartService cartService;

	@Captor
	private ArgumentCaptor<Contacts> contactsCaptor;

	private MockMvc mockMvc;

	private UserAccount account;
	private Principal principal;
	private Cart cart;
	private Product product;
	private Order order;

	@BeforeEach
	public void beforeEach() {
		CheckoutController controller = new CheckoutController(userAccountService, contactsService, orderService, cartService, marketProperties);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setViewResolvers(new InternalResourceViewResolver("/WEB-INF/view/", ".jsp"))
			.build();

		Contacts contacts = FixturesFactory.contacts().build();
		account = FixturesFactory.account()
			.setContacts(contacts)
			.build();
		principal = new PrincipalImpl(account.getEmail());
		cart = new Cart.Builder()
			.setId(account.getId())
			.setUserAccount(account)
			.build();
		Region region = FixturesFactory.region().build();
		Distillery distillery = FixturesFactory.distillery(region).build();
		product = FixturesFactory.product(distillery).build();

		order = FixturesFactory.order(account).build();
		Bill bill = FixturesFactory.bill(order).build();
		order.setBill(bill);
	}

	@Test
	public void getDetailsPage_DeliveryTrue() throws Exception {
		given(cartService.getCartOrCreate(account.getEmail()))
			.willReturn(cart);
		given(contactsService.getContacts(account.getEmail()))
			.willReturn(account.getContacts());

		mockMvc.perform(
			get("/checkout/details")
				.principal(principal))
			.andExpect(status().isOk())
			.andExpect(view().name("checkout/details"))
			.andExpect(model().attribute("userContacts", equalTo(contactsDtoAssembler.toModel(account.getContacts()))));
	}

	@Test
	public void getDetailsPage_DeliveryFalse() throws Exception {
		Cart cartWithoutDelivery = new Cart.Builder(cart)
			.setDeliveryIncluded(false)
			.build();

		given(cartService.getCartOrCreate(account.getEmail()))
			.willReturn(cartWithoutDelivery);

		mockMvc.perform(
			get("/checkout/details")
				.principal(principal))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/checkout/payment"));
	}

	@Test
	public void changeContacts() throws Exception {
		Contacts newContacts = new Contacts.Builder(account.getContacts())
			.setPhone("+71112223344")
			.setAddress("newAddress")
			.build();

		mockMvc.perform(
			post("/checkout/details")
				.principal(principal)
				.param("changeContacts", "changeRequested")
				.param("phone", newContacts.getPhone())
				.param("address", newContacts.getAddress()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/checkout/payment"))
			.andExpect(model().attribute("userContacts", equalTo(contactsDtoAssembler.toModel(newContacts))));

		verify(contactsService).updateUserContacts(contactsCaptor.capture(), eq(account.getEmail()));
		assertThat(contactsCaptor.getValue(), equalTo(newContacts));
	}

	@Test
	public void changeContacts_changeNotRequired() throws Exception {
		Contacts newContacts = new Contacts.Builder(account.getContacts())
			.setPhone("+71112223344")
			.setAddress("newAddress")
			.build();

		given(contactsService.getContacts(account.getEmail()))
			.willReturn(account.getContacts());

		mockMvc.perform(
			post("/checkout/details")
				.principal(principal)
				.param("changeContacts", "doNotChange")
				.param("phone", newContacts.getPhone())
				.param("address", newContacts.getAddress()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/checkout/payment"))
			.andExpect(model().attribute("userContacts", equalTo(contactsDtoAssembler.toModel(account.getContacts()))));
	}

	@Test
	public void getPayment() throws Exception {
		int quantity = 2;
		cart.update(product, quantity);

		given(userAccountService.findByEmail(account.getEmail()))
			.willReturn(account);
		given(cartService.getCartOrCreate(account.getEmail()))
			.willReturn(cart);

		mockMvc.perform(
			get("/checkout/payment")
				.principal(principal))
			.andExpect(status().isOk())
			.andExpect(view().name("checkout/payment"))
			.andExpect(model().attribute("userName", equalTo(account.getName())))
			.andExpect(model().attribute("userContacts", equalTo(contactsDtoAssembler.toModel(account.getContacts()))))
			.andExpect(model().attribute("deliveryCost", equalTo(marketProperties.getDeliveryCost())))
			.andExpect(model().attribute("creditCard", hasProperty("number", is(emptyOrNullString()))))
			.andExpect(model().attribute("productsById", hasEntry(product.getId(), productDtoAssembler.toModel(product))));
	}

	@Test
	public void postPayment() throws Exception {
		given(orderService.createUserOrder(account.getEmail(), marketProperties.getDeliveryCost(), order.getBill().getCcNumber()))
			.willReturn(order);

		mockMvc.perform(
			post("/checkout/payment")
				.principal(principal)
				.param("number", order.getBill().getCcNumber()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/checkout/confirmation"))
			.andExpect(model().hasNoErrors())
			.andExpect(model().attribute("createdOrder", orderDtoAssembler.toModel(order)));
	}

	@Test
	public void getGratitude() throws Exception {
		given(userAccountService.findByEmail(account.getEmail()))
			.willReturn(account);

		mockMvc.perform(
			get("/checkout/confirmation")
				.principal(principal))
			.andExpect(status().isOk())
			.andExpect(view().name("checkout/confirmation"))
			.andExpect(model().attribute("userAccount", equalTo(accountDtoAssembler.toModel(account))))
			.andExpect(model().attribute("userDetails", equalTo(contactsDtoAssembler.toModel(account.getContacts()))));
	}
}
