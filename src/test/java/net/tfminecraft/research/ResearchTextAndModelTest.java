package net.tfminecraft.research;

import net.tfminecraft.research.model.*;
import net.tfminecraft.research.util.GuiText;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTextAndModelTest {
    private ResearchTestState state;
    @BeforeEach void setup() throws Exception { state = new ResearchTestState(); }
    @AfterEach void teardown() throws Exception { state.close(); }
    @Test void messageAndGuiFormattingKeepChatPrefixOutOfGuiAndHandleEmptyValues() {
        Messages.clear(); Messages.setPrefix(null); Messages.put("greeting", "Hello {name}");
        assertFalse(Messages.has(null)); assertFalse(Messages.has("missing")); assertTrue(Messages.has("greeting"));
        assertEquals("missing", Messages.get("missing"));
        Messages.setPrefix("[Research] ");
        assertEquals("[Research] Hello Ryan", Messages.format("greeting",Map.of("name","Ryan")));
        assertEquals("Hello Ryan", GuiText.guiMessage("greeting",Map.of("name","Ryan")));
        assertEquals("Hello {name}", Messages.formatBody("greeting",null));
        Messages.put("empty",null); assertEquals("",Messages.formatBody("empty",null));
        assertEquals("",GuiText.format(null)); assertEquals("",GuiText.label(null)); assertEquals("label",GuiText.label("label"));
        GuiCache.resetColors(null); assertEquals("#ffffff",GuiText.color("missing"));
        GuiCache.resetColors(Map.of());
        Map<String,String> colors = new HashMap<>(); colors.put("aspect_confirmed","#112233"); colors.put("blank"," ");
        GuiCache.resetColors(colors); colors.clear();
        assertEquals("#112233",GuiText.color("aspect_confirmed")); assertEquals("fallback",GuiCache.color("blank","fallback"));
        assertThrows(UnsupportedOperationException.class, () -> GuiCache.colors.put("x","y"));
        assertEquals("",ChatColor.stripColor(GuiText.text("missing",null)));
        assertEquals("word",ChatColor.stripColor(GuiText.text("missing","word")));
        assertEquals(List.of(),GuiText.formatLoreLines(null)); assertEquals(List.of(),GuiText.formatLoreLines(List.of()));
        assertEquals(List.of("one",""),GuiText.formatLoreLines(Arrays.asList("one",null)));
        GuiCache.undiscoveredAspectName = "Hidden"; assertEquals("Hidden",GuiText.undiscoveredAspectName());
        GuiCache.undiscoveredAspectName = " "; assertEquals("???",ChatColor.stripColor(GuiText.undiscoveredAspectName()));
        GuiCache.undiscoveredAspectName = null; assertEquals("???",ChatColor.stripColor(GuiText.undiscoveredAspectName()));
        GuiCache.undiscoveredAspectLore = List.of("hint"); assertEquals(List.of("hint"),GuiText.undiscoveredAspectLore());
        for (var style : GuiText.AspectNameStyle.values()) assertEquals("name",ChatColor.stripColor(GuiText.aspectName(style,"§aname")));
        assertEquals("",GuiText.aspectName(GuiText.AspectNameStyle.PLAIN,null));
    }
    @Test void completionEventPublishesStablePluginApi() {
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var event = new net.tfminecraft.research.event.ResearchCompleteEvent(player,"input","output","v.paper");
        assertSame(player,event.getPlayer()); assertEquals("input",event.getInputId());
        assertEquals("output",event.getOutputId()); assertEquals(event.getOutputId(),event.getProjectId());
        assertEquals("v.paper",event.getResultItemRef());
        assertSame(net.tfminecraft.research.event.ResearchCompleteEvent.getHandlerList(),event.getHandlers());
    }
    @Test void modelAccessorsExposeConfiguredValuesAndDefensiveCollections() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                name: Flame
                lore: [first]
                display: {item: v.paper}
                grid_color: RED
                primary_items: [v.paper, ' ']
                secondary_items: [v.stone]
                """);
        AspectDef aspect = new AspectDef("fire",config);
        assertEquals("fire",aspect.getId()); assertEquals("Flame",aspect.getName()); assertEquals("RED",aspect.getGridColor());
        assertEquals("vanilla.RED_STAINED_GLASS_PANE",aspect.getPulseColorRef());
        List<String> lore = aspect.getLore(); lore.clear(); assertEquals(List.of("first"),aspect.getLore());
        assertEquals(List.of("v.paper"),aspect.getPrimaryItems()); assertEquals(List.of("v.stone"),aspect.getSecondaryItems());
        ResultTemplateDef empty = new ResultTemplateDef("empty",(org.bukkit.configuration.ConfigurationSection)null);
        assertEquals("empty",empty.getId()); assertTrue(empty.getOutputs().isEmpty());
        config.loadFromString("product_reveal: {base_after_confirmed_aspects: 2}\naspects: {fire: scalar}\n");
        OutputDef output = new OutputDef("out",config);
        assertEquals(2,output.getProductRevealAfterConfirmedAspects());
        assertNotNull(output.getAspects().get("fire"));
        assertThrows(UnsupportedOperationException.class, () -> output.getAspects().clear());
    }
}
