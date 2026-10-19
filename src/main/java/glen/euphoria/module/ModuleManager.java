package glen.euphoria.module;

import glen.euphoria.module.modules.client.ClientSettingsModule;
import glen.euphoria.module.modules.client.Debugger;
import glen.euphoria.module.modules.client.SelfDestruct;
import glen.euphoria.module.modules.combat.*;
import glen.euphoria.module.modules.misc.*;
import glen.euphoria.module.modules.movement.*;
import glen.euphoria.module.modules.player.*;
import glen.euphoria.module.modules.render.*;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public final class ModuleManager {

    private final List<Module> modules = new java.util.ArrayList<>();

    public ModuleManager() {
        addModules();
    }

    public List<Module> getEnabledModules() {
        return modules.stream()
                .filter(Module::isEnabled)
                .toList();
    }

    public List<Module> getModulesInCategory(Category category) {
        return modules.stream()
                .filter(module -> module.getModuleCategory() == category)
                .toList();
    }

    public List<Module> getModulesByCategory(Category category) {
        return getModulesInCategory(category);
    }

    public <T extends Module> Optional<T> getModule(Class<T> moduleClass) {
        if (SelfDestruct.isDestroyed()) {
            return Optional.empty();
        }
        return modules.stream()
                .filter(module -> module.getClass().equals(moduleClass))
                .map(moduleClass::cast)
                .findFirst();
    }

    private void addModules() {
        // Combat
        add(
                new AimAssist(), new AntiMiss(), new AutoCart(),
                new AutoInventoryTotem(), new AutoMace(), new AutoPot(),
                new AutoAnchor(), new BreachSwap(), new CartKey(),
                new Criticals(), new AutoCrystal(), new CrystalOptimizer(),
                new ElytraHotSwap(), new FastEXP(), new Hitboxes(),
                new HoverTotem(),
                new KeyLava(),
                new PearlCatch(), new PearlKey(),
                new STap(), new ShieldBreaker(),
                new SwordHotSwap(), new SwordSwap(),
                new ThrowPot(), new TriggerBot(),
                new Velocity(), new WindChargeKey(), new WTap(), new XbowCart());

        // Movement
        add(new AutoFirework(), new AutoHeadHitter(), new KeepSprint(), new SnapTap(), new Sprint());

        // Player
        add(
                new AutoCrafter(), new AutoDrain(), new AutoExtinguish(),
                new AutoMLG(), new AutoRefill(), new AutoTool(), new AutoWeb(),
                new CoverUp(), new FastMine(), new FastPlace(),
                new PingSpoof(), new ReBuffNotifier(), new TrapSave());

        // Render
        add(
                new ArrayList(), new ArrowESP(),
                new ESP2D(), new ESP3D(), new FullBright(),
                new Nametags(), new Notifications(), new OutlineESP(),
                new SwingSpeed(), new TargetESP(), new TargetHUD(),
                new Trajectories(), new Watermark());

        // Misc
        add(
                new FakePlayer(), new Friends(),
                new Impersonation(), new MiddleClickFriend(), new Teams());

        // Client
        add(new ClientSettingsModule(), new Debugger(),
                new SelfDestruct());
    }

    private void add(Module... mods) {
        modules.addAll(Arrays.asList(mods));
    }
}