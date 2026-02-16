export const createRandomFill = ({
  ENVIRONMENT_IS_NODE,
  require,
  abort,
}: {
  ENVIRONMENT_IS_NODE: boolean;
  require?: (id: string) => any;
  abort: (message?: string) => never;
}) => {
var initRandomFill = () => {
        if (typeof crypto == "object" && typeof crypto["getRandomValues"] == "function") { return (view: any) => crypto.getRandomValues(view) } else if (ENVIRONMENT_IS_NODE) {
          try {
            var crypto_module = require("crypto");
            var randomFillSync = crypto_module["randomFillSync"];
            if (randomFillSync) { return (view: any) => crypto_module["randomFillSync"](view) } var randomBytes = crypto_module["randomBytes"];
            return (view: any) => (view.set(randomBytes(view.byteLength)), view)
          } catch (e) { }
        } abort("initRandomDevice")
      };
      var randomFill = (view: any) => (randomFill = initRandomFill())(view);
      
return { randomFill, initRandomFill };
};
