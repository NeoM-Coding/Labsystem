package xyz.jasenon.lab.engine.eval.v2;

/**
 * 一元布尔函数的完整表示。
 *
 * <p>布尔输入只有 false 和 true 两种可能，因此保存这两个输入对应的输出，
 * 就足以完成表达式片段的函数复合，无需物化任何中间匹配结果。</p>
 */
public record BooleanTransform(boolean onFalse, boolean onTrue) {

    public static BooleanTransform constant(boolean value) {
        return new BooleanTransform(value, value);
    }

    public static BooleanTransform and(boolean value) {
        return new BooleanTransform(false, value);
    }

    public static BooleanTransform or(boolean value) {
        return new BooleanTransform(value, true);
    }

    public boolean apply(boolean input) {
        return input ? onTrue : onFalse;
    }

    /**
     * 返回 {@code next ∘ this}，即先应用当前函数，再应用 next。
     */
    public BooleanTransform then(BooleanTransform next) {
        return new BooleanTransform(next.apply(onFalse), next.apply(onTrue));
    }
}
