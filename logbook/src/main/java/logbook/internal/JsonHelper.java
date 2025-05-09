package logbook.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * JsonObjectを扱うための補助クラスです
 *
 */
public final class JsonHelper {

    /**
     * JsonNumberをLongに変換します
     *
     * @see JsonNumber#longValue()
     * @see BigDecimal#longValue()
     * @param val Longに変換するJsonValue
     * @return Long
     */
    public static Long toLong(JsonValue val) {
        if (val instanceof JsonNumber) {
            return ((JsonNumber) val).longValue();
        }
        return new BigDecimal(toString(val)).longValue();
    }

    /**
     * JsonValueをIntegerに変換します<br>
     * 値が"N/A"の場合、nullを設定します
     *
     * @see JsonNumber#intValue()
     * @see BigDecimal#intValue()
     * @param val Integerに変換するJsonValue
     * @return Integer
     */
    public static Integer toInteger(JsonValue val) {
        if (val instanceof JsonNumber) {
            return ((JsonNumber) val).intValue();
        }
        if (val instanceof JsonString) {
            if(((JsonString) val).getString().equals("N/A"))return null;
        }
        return new BigDecimal(toString(val)).intValue();
    }

    /**
     * JsonValueをDoubleに変換します
     *
     * @see JsonNumber#doubleValue()
     * @see BigDecimal#doubleValue()
     * @param val Doubleに変換するJsonValue
     * @return Double
     */
    public static Double toDouble(JsonValue val) {
        if (val instanceof JsonNumber) {
            return ((JsonNumber) val).doubleValue();
        }
        return new BigDecimal(toString(val)).doubleValue();
    }

    /**
     * JsonValueをBigDecimalに変換します
     *
     * @see JsonNumber#bigDecimalValue()
     * @param val BigDecimalに変換するJsonValue
     * @return BigDecimal
     */
    public static BigDecimal toBigDecimal(JsonValue val) {
        if (val instanceof JsonNumber) {
            return ((JsonNumber) val).bigDecimalValue();
        }
        return new BigDecimal(toString(val));
    }

    /**
     * JsonValueをStringに変換します
     *
     * @see JsonString#getString()
     * @see JsonValue#toString()
     * @param val Stringに変換するJsonValue
     * @return String
     */
    public static String toString(JsonValue val) {
        if (val instanceof JsonString) {
            return ((JsonString) val).getString();
        }
        return toObject(val, Object::toString);
    }

    /**
     * JsonValueをBooleanに変換します
     *
     * @param val Booleanに変換するJsonValue
     * @return JsonNumber の場合、 BigDecimal.ZEROと等しくない場合 true、BigDecimal.ZEROと等しい場合はfalse<br>
     * JsonNumber 以外の場合、JsonValue.FALSEと等しくない場合 true、sonValue.FALSEと等しい場合はfalse<br>
     */
    public static Boolean toBoolean(JsonValue val) {
        return toObject(val, v -> {
            if (v instanceof JsonNumber) {
                // JsonNumber の場合、 BigDecimal.ZEROと等しくない場合 true、それ以外はfalse
                return BigDecimal.ZERO.compareTo(((JsonNumber) v).bigDecimalValue()) != 0;
            }
            return v != JsonValue.FALSE;
        });
    }

    /**
     * JsonValueを任意のオブジェクトに変換します
     *
     * @param <T> JsonValueの実際の型
     * @param <R> functionの戻り値の型
     * @param val 型Rに変換するJsonValue
     * @param function 変換Function
     * @return 変換後のオブジェクト
     */
    public static <T extends JsonValue, R> R toObject(T val, Function<T, R> function) {
        if (val == null || val == JsonValue.NULL) {
            return null;
        }
        return function.apply(val);
    }

    /**
     * JsonArrayをLongのListに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return LongのList
     */
    public static List<Long> toLongList(JsonArray val) {
        return toList(val, JsonHelper::toLong);
    }

    /**
     * JsonValueをLongのListに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptyList()}を返します。<br>
     *
     * @param val 変換するJsonValue
     * @return LongのList
     */
    public static List<Long> checkedToLongList(JsonValue val) {
        if (val instanceof JsonArray) {
            return toList((JsonArray) val, JsonHelper::toLong);
        }
        return Collections.emptyList();
    }

    /**
     * JsonArrayをIntegerのListに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return IntegerのList
     */
    public static List<Integer> toIntegerList(JsonArray val) {
        return toList(val, JsonHelper::toInteger);
    }

    /**
     * JsonValueをIntegerのListに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptyList()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return IntegerのList
     */
    public static List<Integer> checkedToIntegerList(JsonValue val) {
        if (val instanceof JsonArray) {
            return toList((JsonArray) val, JsonHelper::toInteger);
        }
        return Collections.emptyList();
    }

    /**
     * JsonArrayをDoubleのListに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return DoubleのList
     */
    public static List<Double> toDoubleList(JsonArray val) {
        return toList(val, JsonHelper::toDouble);
    }

    /**
     * JsonValueをDoubleのListに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptyList()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return DoubleのList
     */
    public static List<Double> checkedToDoubleList(JsonValue val) {
        if (val instanceof JsonArray) {
            return toList((JsonArray) val, JsonHelper::toDouble);
        }
        return Collections.emptyList();
    }

    /**
     * JsonArrayをBigDecimalのListに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return BigDecimalのList
     */
    public static List<BigDecimal> toBigDecimalList(JsonArray val) {
        return toList(val, JsonHelper::toBigDecimal);
    }

    /**
     * JsonValueをBigDecimalのListに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptyList()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return BigDecimalのList
     */
    public static List<BigDecimal> checkedToBigDecimalList(JsonValue val) {
        if (val instanceof JsonArray) {
            return toList((JsonArray) val, JsonHelper::toBigDecimal);
        }
        return Collections.emptyList();
    }

    /**
     * JsonArrayをStringのListに変換します<br>
     *
     * @param val 変換するJsonArray
     * @return StringのList
     */
    public static List<String> toStringList(JsonArray val) {
        return toList(val, JsonHelper::toString);
    }

    /**
     * JsonValueをStringのListに変換します<br>
     * valがJsonArrayではない場合{@code Collections#emptyList()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return StringのList
     */
    public static List<String> checkedToStringList(JsonValue val) {
        if (val instanceof JsonArray) {
            return toList((JsonArray) val, JsonHelper::toString);
        }
        return Collections.emptyList();
    }

    /**
     * JsonArrayをLongのSetに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return LongのSet
     */
    public static Set<Long> toLongSet(JsonArray val) {
        return toSet(val, JsonHelper::toLong);
    }

    /**
     * JsonValueをLongのSetに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptySet()}を返します。<br>
     *
     * @param val 変換するJsonValue
     * @return LongのSet
     */
    public static Set<Long> checkedToLongSet(JsonValue val) {
        if (val instanceof JsonArray) {
            return toSet((JsonArray) val, JsonHelper::toLong);
        }
        return Collections.emptySet();
    }

    /**
     * JsonArrayをIntegerのSetに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return IntegerのSet
     */
    public static Set<Integer> toIntegerSet(JsonArray val) {
        return toSet(val, JsonHelper::toInteger);
    }

    /**
     * JsonValueをIntegerのSetに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptySet()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return IntegerのSet
     */
    public static Set<Integer> checkedToIntegerSet(JsonValue val) {
        if (val instanceof JsonArray) {
            return toSet((JsonArray) val, JsonHelper::toInteger);
        }
        return Collections.emptySet();
    }

    /**
     * JsonArrayをDoubleのSetに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return DoubleのSet
     */
    public static Set<Double> toDoubleSet(JsonArray val) {
        return toSet(val, JsonHelper::toDouble);
    }

    /**
     * JsonValueをDoubleのSetに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptySet()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return DoubleのSet
     */
    public static Set<Double> checkedToDoubleSet(JsonValue val) {
        if (val instanceof JsonArray) {
            return toSet((JsonArray) val, JsonHelper::toDouble);
        }
        return Collections.emptySet();
    }

    /**
     * JsonArrayをBigDecimalのSetに変換します<br>
     * JsonArrayの内容はすべてJsonNumberである必要があります
     *
     * @param val 変換するJsonArray
     * @return BigDecimalのSet
     */
    public static Set<BigDecimal> toBigDecimalSet(JsonArray val) {
        return toSet(val, JsonHelper::toBigDecimal);
    }

    /**
     * JsonValueをBigDecimalのSetに変換します<br>
     * valのJsonArrayの場合、内容はすべてJsonNumberである必要があります<br>
     * valがJsonArrayではない場合{@code Collections#emptySet()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return BigDecimalのSet
     */
    public static Set<BigDecimal> checkedToBigDecimalSet(JsonValue val) {
        if (val instanceof JsonArray) {
            return toSet((JsonArray) val, JsonHelper::toBigDecimal);
        }
        return Collections.emptySet();
    }

    /**
     * JsonArrayをStringのSetに変換します<br>
     *
     * @param val 変換するJsonArray
     * @return StringのSet
     */
    public static Set<String> toStringSet(JsonArray val) {
        return toSet(val, JsonHelper::toString);
    }

    /**
     * JsonValueをStringのSetに変換します<br>
     * valがJsonArrayではない場合{@code Collections#emptySet()}を返します。<br>
     *
     * @param val 変換するJsonArray
     * @return StringのSet
     */
    public static Set<String> checkedToStringSet(JsonValue val) {
        if (val instanceof JsonArray) {
            return toSet((JsonArray) val, JsonHelper::toString);
        }
        return Collections.emptySet();
    }

    /**
     * JsonArrayまたはJsonObjectをCollectionに変換します<br>
     * JsonObjectの場合は単一のオブジェクトだけを格納しているCollectionを返します
     *
     * @param <T> JsonArrayの内容の型
     * @param <R> functionの戻り値の型
     * @param <C> 変換後のCollectionの型
     * @param value 変換するJsonArrayまたはJsonObject
     * @param function JsonValueを受け取って変換するFunction
     * @param supplier Collectionインスタンスを供給するSupplier
     * @return 変換後のCollection
     */
    @SuppressWarnings("unchecked")
    public static <T extends JsonValue, C extends Collection<R>, R> C toCollection(JsonValue value,
            Function<T, R> function, Supplier<C> supplier) {
        C collection = supplier.get();
        if (value instanceof JsonArray) {
            for (JsonValue val : (JsonArray) value) {
                if (val == null || val == JsonValue.NULL) {
                    collection.add(null);
                } else {
                    collection.add(function.apply((T) val));
                }
            }
        } else {
            collection.add(function.apply((T) value));
        }
        return collection;
    }

    /**
     * JsonArrayをListに変換します
     *
     * @param <T> JsonArrayの内容の型
     * @param <R> functionの戻り値の型
     * @param value 変換するJsonArrayまたはJsonObject
     * @param function JsonValueを受け取って変換するFunction
     * @return 変換後のList
     */
    public static <T extends JsonValue, R> List<R> toList(JsonValue value, Function<T, R> function) {
        return toCollection(value, function, ArrayList::new);
    }

    /**
     * JsonArrayをListに変換する関数を返します
     *
     * @param <T> JsonArrayの内容の型
     * @param <R> functionの戻り値の型
     * @param function JsonValueを受け取って変換するFunction
     * @return Listに変換する関数
     */
    public static <T extends JsonValue, R> Function<JsonValue, List<R>> toList(Function<T, R> function) {
        return val -> JsonHelper.toList(val, function);
    }

    /**
     * JsonArrayをSetに変換します
     *
     * @param <T> JsonArrayの内容の型
     * @param <R> functionの戻り値の型
     * @param value 変換するJsonArrayまたはJsonObject
     * @param function JsonValueを受け取って変換するFunction
     * @return 変換後のSet
     */
    public static <T extends JsonValue, R> Set<R> toSet(JsonValue value, Function<T, R> function) {
        return toCollection(value, function, LinkedHashSet::new);
    }

    /**
     * JsonArrayをSetに変換する関数を返します
     *
     * @param <T> JsonArrayの内容の型
     * @param <R> functionの戻り値の型
     * @param function JsonValueを受け取って変換するFunction
     * @return Setに変換する関数
     */
    public static <T extends JsonValue, R> Function<JsonValue, Set<R>> toSet(Function<T, R> function) {
        return val -> JsonHelper.toSet(val, function);
    }

    /**
     * JsonArrayをMapに変換します
     *
     * @param <T> JsonArrayの内容の型
     * @param <K> Mapのキーの型
     * @param <R> Mapの内容の型
     * @param array 変換するJsonArray
     * @param keyMapper valueMapperで変換したオブジェクトからキーを取り出すFunction
     * @param valueMapper array内のJsonValueを変換するFunction
     * @return 変換後のMap
     */
    @SuppressWarnings("unchecked")
    public static <T extends JsonValue, K, R> Map<K, R> toMap(JsonArray array, Function<R, K> keyMapper,
            Function<T, R> valueMapper) {
        Map<K, R> map = new LinkedHashMap<>();
        for (JsonValue val : array) {
            R r = valueMapper.apply((T) val);
            map.put(keyMapper.apply(r), r);
        }
        return map;
    }

    /**
     * JsonObjectをMapに変換します
     *
     * @param <T> JsonObjectの内容の型
     * @param <K> Mapのキーの型
     * @param <R> Mapの内容の型
     * @param obj 変換するJsonObject
     * @param keyMapper JsonObjectのキーからキーを取り出すFunction
     * @param valueMapper array内のJsonValueを変換するFunction
     * @return 変換後のMap
     */
    @SuppressWarnings("unchecked")
    public static <T extends JsonValue, K, R> Map<K, R> toMap(JsonObject obj, Function<String, K> keyMapper,
            Function<T, R> valueMapper) {
        Map<K, R> map = new LinkedHashMap<>();
        obj.forEach((k, v) -> {
            R r = valueMapper.apply((T) v);
            map.put(keyMapper.apply(k), r);
        });
        return map;
    }

    /**
     * JsonObjectから別のオブジェクトへの単方向バインディングを提供します。<br>
     * <br>
     * 次の例はbeanにJSONからの値を設定する例です。<br>
     * JSON例<br>
     * <pre><code>{"api_id" : 558, "api_name" : "深海復讐艦攻改", "api_type" : [ 3, 5, 8, 8 ]}</code></pre>
     * Javaコード例<br>
     * <pre><code>JsonHelper.bind(json)
     *      .set("api_id", bean::setId, JsonHelper::toInteger)
     *      .set("api_name", bean::setName, JsonHelper::toString)
     *      .set("api_type", bean::setType, JsonHelper::toIntegerList);</code></pre>
     *
     * @param json JsonObject
     * @return {@link Bind}
     */
    public static Bind bind(JsonObject json) {
        return new Bind(json);
    }

    /**
     * JsonObjectから別のオブジェクトへの単方向バインディングを提供します。<br>
     * <br>
     * 次の例はbeanにJSONからの値を設定する例です。<br>
     * JSON例<br>
     * <pre><code>{"api_id" : 558, "api_name" : "深海復讐艦攻改", "api_type" : [ 3, 5, 8, 8 ]}</code></pre>
     * Javaコード例<br>
     * <pre><code>JsonHelper.bind(json)
     *      .set("api_id", bean::setId, JsonHelper::toInteger)
     *      .set("api_name", bean::setName, JsonHelper::toString)
     *      .set("api_type", bean::setType, JsonHelper::toIntegerList);</code></pre>
     *
     * @param json JsonObject
     * @param listener BindListener
     * @return {@link Bind}
     */
    public static Bind bind(JsonObject json, BindListener listener) {
        return new Bind(json, listener);
    }

    /**
     * JsonObjectから別のオブジェクトへの単方向バインディングを提供します。<br>
     *
     */
    public static class Bind {

        private JsonObject json;

        private BindListener listener;

        /**
         * コンストラクター
         *
         * @param json JsonObject
         */
        private Bind(JsonObject json) {
            this.json = json;
        }

        /**
         * コンストラクター
         *
         * @param json JsonObject
         * @param listener BindListener
         */
        private Bind(JsonObject json, BindListener listener) {
            this.json = json;
            this.listener = listener;
        }

        /**
         * keyで取得したJsonValueをconverterで変換したものをconsumerへ設定します<br>
         *
         * @param <T> JsonObject#get(Object) の戻り値の型
         * @param <R> converterの戻り値の型
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @param converter JsonValueを変換するFunction
         * @return {@link Bind}
         */
        @SuppressWarnings("unchecked")
        public <T extends JsonValue, R> Bind set(String key, Consumer<R> consumer, Function<T, R> converter) {
            JsonValue val = this.json.get(key);
            if (val != null && JsonValue.NULL != val) {
                R obj = converter.apply((T) val);
                consumer.accept(obj);
                if (this.listener != null) {
                    this.listener.apply(key, val, obj);
                }
            }
            return this;
        }

        /**
         * keyで取得したJsonValueをList<Integer>に変換したものをconsumerへ設定します<br>
         *
         * @param <T> JsonObject#get(Object) の戻り値の型
         * @param key JsonObjectから取得するキー
         * @param consumer List<Integer>を消費するConsumer
         * @return {@link Bind}
         */
        public <T extends JsonArray> Bind setIntegerList(String key, Consumer<List<Integer>> consumer) {
            return this.set(key, consumer, JsonHelper::toIntegerList);
        }


        /**
         * keyで取得したJsonValueをList<Long>に変換したものをconsumerへ設定します<br>
         *
         * @param <T> JsonObject#get(Object) の戻り値の型
         * @param key JsonObjectから取得するキー
         * @param consumer List<Long>を消費するConsumer
         * @return {@link Bind}
         */
        public <T extends JsonArray> Bind setLongList(String key, Consumer<List<Long>> consumer) {
            return this.set(key, consumer, JsonHelper::toLongList);
        }

        /**
         * keyで取得したJsonValueをList<Double>に変換したものをconsumerへ設定します<br>
         *
         * @param <T> JsonObject#get(Object) の戻り値の型
         * @param key JsonObjectから取得するキー
         * @param consumer List<Double>を消費するConsumer
         * @return {@link Bind}
         */
        public <T extends JsonArray> Bind setDoubleList(String key, Consumer<List<Double>> consumer) {
            return this.set(key, consumer, JsonHelper::toDoubleList);
        }

        /**
         * keyで取得したJsonValueをList<String>に変換したものをconsumerへ設定します<br>
         *
         * @param <T> JsonObject#get(Object) の戻り値の型
         * @param key JsonObjectから取得するキー
         * @param consumer List<Integer>を消費するConsumer
         * @return {@link Bind}
         */
        public <T extends JsonArray> Bind setStringList(String key, Consumer<List<String>> consumer) {
            return this.set(key, consumer, JsonHelper::toStringList);
        }

        /**
         * keyで取得したJsonValueをStringに変換しconsumerへ設定します<br>
         *
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @return {@link Bind}
         */
        public Bind setString(String key, Consumer<String> consumer) {
            return this.set(key, consumer, JsonHelper::toString);
        }

        /**
         * keyで取得したJsonValueをIntegerに変換しconsumerへ設定します<br>
         *
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @return {@link Bind}
         */
        public Bind setInteger(String key, Consumer<Integer> consumer) {
            return this.set(key, consumer, JsonHelper::toInteger);
        }

        /**
         * keyで取得したJsonValueをLongに変換しconsumerへ設定します<br>
         *
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @return {@link Bind}
         */
        public Bind setLong(String key, Consumer<Long> consumer) {
            return this.set(key, consumer, JsonHelper::toLong);
        }

        /**
         * keyで取得したJsonValueをDoubleに変換しconsumerへ設定します<br>
         *
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @return {@link Bind}
         */
        public Bind setDouble(String key, Consumer<Double> consumer) {
            return this.set(key, consumer, JsonHelper::toDouble);
        }

        /**
         * keyで取得したJsonValueをBigDecimalに変換しconsumerへ設定します<br>
         *
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @return {@link Bind}
         */
        public Bind setBigDecimal(String key, Consumer<BigDecimal> consumer) {
            return this.set(key, consumer, JsonHelper::toBigDecimal);
        }

        /**
         * keyで取得したJsonValueをBooleanに変換しconsumerへ設定します<br>
         *
         * @param key JsonObjectから取得するキー
         * @param consumer converterの戻り値を消費するConsumer
         * @return {@link Bind}
         */
        public Bind setBoolean(String key, Consumer<Boolean> consumer) {
            return this.set(key, consumer, JsonHelper::toBoolean);
        }
    }

    /**
     * {@link Bind}によって設定される値を監視するためのリスナー
     */
    public static interface BindListener {

        /**
         * 設定される値を監視します
         *
         * @param key JsonObjectのキー
         * @param val JsonObjectの値
         * @param obj converterより返された値
         */
        void apply(String key, JsonValue val, Object obj);
    }
}
