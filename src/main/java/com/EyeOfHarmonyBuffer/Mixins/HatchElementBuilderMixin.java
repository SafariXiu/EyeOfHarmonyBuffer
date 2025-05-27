package com.EyeOfHarmonyBuffer.Mixins;

import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("UnusedMixin")
@Mixin(value = HatchElementBuilder.class, remap = false)
public class HatchElementBuilderMixin<T> {

    @Inject(
        method = "atLeast(Ljava/util/Map;)Lgregtech/api/util/HatchElementBuilder;",
        at = @At("HEAD"),
        cancellable = true)
    @SuppressWarnings("unchecked")
    private void injectCustomAtLeastMap(Map<IHatchElement<? super T>, ? extends Number> elements,
        CallbackInfoReturnable<HatchElementBuilder<T>> cir) {
        if (elements == null || elements.isEmpty() || elements.containsKey(null) || elements.containsValue(null)) {
            throw new IllegalArgumentException();
        }

        Map<IHatchElement<? super T>, Long> modified = new LinkedHashMap<>();
        for (Map.Entry<IHatchElement<? super T>, ? extends Number> entry : elements.entrySet()) {
            modified.put(
                entry.getKey(),
                entry.getValue()
                    .longValue());
        }
        IHatchElement<? super T> exotic = (IHatchElement<? super T>) HatchElement.ExoticEnergy;
        modified.putIfAbsent(exotic, 1L);

        List<Class<? extends IMetaTileEntity>> list = modified.keySet()
            .stream()
            .map(IHatchElement::mteClasses)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        HatchElementBuilder<T> builder = ((HatchElementBuilder<T>) (Object) this);
        HatchElementBuilder<T> result = builder.adder(
            modified.keySet()
                .stream()
                .map(
                    e -> e.adder()
                        .rebrand())
                .reduce(IGTHatchAdder::orElse)
                .orElseThrow(AssertionError::new))
            .hatchItemFilter(
                obj -> GTStructureUtility.filterByMTEClass(
                    modified.entrySet()
                        .stream()
                        .filter(
                            entry -> entry.getKey()
                                .count(obj) < entry.getValue())
                        .flatMap(
                            entry -> entry.getKey()
                                .mteClasses()
                                .stream())
                        .collect(Collectors.toList())))
            .shouldReject(
                obj -> modified.entrySet()
                    .stream()
                    .allMatch(
                        e -> e.getKey()
                            .count(obj) >= e.getValue()))
            .shouldSkip(
                (c, t) -> t != null && list.stream()
                    .anyMatch(clazz -> clazz.isInstance(t.getMetaTileEntity())))
            .cacheHint(
                () -> modified.keySet()
                    .stream()
                    .map(IHatchElement::name)
                    .sorted()
                    .collect(Collectors.joining(" or ", "of type ", "")));

        cir.setReturnValue(result);
    }
}
