package market.controller.backend;

import market.domain.Region;
import market.dto.assembler.RegionDtoAssembler;
import market.service.RegionService;
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

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RegionController.class)
public class RegionControllerTest {
	private final RegionDtoAssembler regionDtoAssembler = new RegionDtoAssembler();

	@MockBean
	private RegionService regionService;

	@Captor
	private ArgumentCaptor<Region> regionCaptor;
	@Captor
	private ArgumentCaptor<Long> longCaptor;

	private MockMvc mockMvc;

	private Region region;

	@BeforeEach
	public void beforeEach() {
		RegionController controller = new RegionController(regionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setViewResolvers(new InternalResourceViewResolver("/WEB-INF/view/", ".jsp"))
			.build();

		region = FixturesFactory.region().build();
	}

	@Test
	public void allRegions() throws Exception {
		List<Region> totalRegions = Collections.singletonList(region);

		given(regionService.findAll())
			.willReturn(totalRegions);

		mockMvc.perform(get("/admin/regions"))
			.andExpect(status().isOk())
			.andExpect(view().name("admin/regions"))
			.andExpect(model().attribute("regions", contains(regionDtoAssembler.toDtoArray(totalRegions))));
	}

	@Test
	public void newRegion() throws Exception {
		mockMvc.perform(get("/admin/regions/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("admin/regions/new"))
			.andExpect(model().attributeExists("region"));
	}

	@Test
	public void postRegion() throws Exception {
		Region regionWithoutId = new Region.Builder(region)
			.setId(null)
			.build();

		mockMvc.perform(
			post("/admin/regions") // todo: should post to /new
				.param("name", region.getName())
				.param("subtitle", region.getSubtitle())
				.param("description", region.getDescription())
				.param("color", region.getColor()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/admin/regions"));

		verify(regionService).create(regionCaptor.capture());
		assertThat(regionCaptor.getValue(), equalTo(regionWithoutId));
	}

	@Test
	public void editRegion() throws Exception {
		given(regionService.findOne(region.getId()))
			.willReturn(region);

		mockMvc.perform(get("/admin/regions/" + region.getId() + "/edit"))
			.andExpect(status().isOk())
			.andExpect(view().name("admin/regions/edit"))
			.andExpect(model().attribute("region", regionDtoAssembler.toModel(region)));
	}

	@Test
	public void putRegion() throws Exception {
		Region changedRegion = new Region.Builder(region)
			.setId(null)
			.setName(region.getName() + "_changed")
			.setSubtitle(region.getSubtitle() + "_changed")
			.setDescription(region.getDescription() + "_changed")
			.setColor("#000000")
			.build();

		mockMvc.perform(
			put("/admin/regions/" + region.getId())
				.param("name", changedRegion.getName())
				.param("subtitle", changedRegion.getSubtitle())
				.param("description", changedRegion.getDescription())
				.param("color", changedRegion.getColor()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/admin/regions"));

		verify(regionService).update(eq(region.getId()), regionCaptor.capture());
		assertThat(regionCaptor.getValue(), equalTo(changedRegion));
	}

	@Test
	public void deleteRegion() throws Exception {
		mockMvc.perform(post("/admin/regions/" + region.getId() + "/delete"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/admin/regions"));

		verify(regionService).delete(longCaptor.capture());
		assertThat(longCaptor.getValue(), equalTo(region.getId()));
	}
}
