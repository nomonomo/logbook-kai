package logbook.proxy;

/**
 * 設定再読み込みの結果を表すsealed interface。
 * 成功または失敗のいずれかの状態を型安全に表現する。
 * 
 * <p>使用例：</p>
 * <pre>{@code
 * ConfigReloadResult result = proxyServer.reloadConfig(config);
 * switch (result) {
 *     case ConfigReloadResult.Success() -> {
 *         // 成功時の処理
 *     }
 *     case ConfigReloadResult.Failure(String message, String fieldName) -> {
 *         // 失敗時の処理
 *         log.error("Failed: {}", message);
 *     }
 * }
 * }</pre>
 */
public sealed interface ConfigReloadResult {
    
    /**
     * 成功結果を作成する。
     * 
     * @return 成功を表す結果
     */
    static Success success() {
        return new Success();
    }
    
    /**
     * 失敗結果を作成する。
     * 
     * @param message エラーメッセージ
     * @return 失敗を表す結果
     */
    static Failure failure(String message) {
        return new Failure(message, null);
    }
    
    /**
     * 失敗結果を作成する（項目名付き）。
     * 
     * @param message エラーメッセージ
     * @param fieldName 失敗した項目名
     * @return 失敗を表す結果
     */
    static Failure failure(String message, String fieldName) {
        return new Failure(message, fieldName);
    }
    
    /**
     * 成功かどうかを判定する。
     * 
     * @return 成功の場合true
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }
    
    /**
     * 失敗かどうかを判定する。
     * 
     * @return 失敗の場合true
     */
    default boolean isFailure() {
        return this instanceof Failure;
    }
    
    /**
     * 成功を表す結果。
     */
    record Success() implements ConfigReloadResult {
    }
    
    /**
     * 失敗を表す結果。
     * 
     * @param message エラーメッセージ
     * @param fieldName 失敗した項目名（オプション、nullの場合もある）
     */
    record Failure(String message, String fieldName) implements ConfigReloadResult {
        
        /**
         * エラーメッセージを取得する（項目名がある場合は含める）。
         * 
         * @return フォーマット済みメッセージ
         */
        public String getFormattedMessage() {
            if (fieldName != null && !fieldName.isEmpty()) {
                return String.format("[%s] %s", fieldName, message);
            }
            return message;
        }
    }
}

