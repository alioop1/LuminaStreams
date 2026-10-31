// Node 23+ removed the old util.isXxx helpers that the LG webOS CLI's ssh code
// still calls ("isDate is not a function" during ares-install). Preloaded into
// the CLI by build.js through NODE_OPTIONS; puts them back if missing.
'use strict';
const util = require('util');
const shims = {
  isArray: Array.isArray,
  isBoolean: v => typeof v === 'boolean',
  isBuffer: v => Buffer.isBuffer(v),
  isDate: v => v instanceof Date,
  isError: v => v instanceof Error,
  isFunction: v => typeof v === 'function',
  isNull: v => v === null,
  isNullOrUndefined: v => v == null,
  isNumber: v => typeof v === 'number',
  isObject: v => v !== null && typeof v === 'object',
  isPrimitive: v => v === null || (typeof v !== 'object' && typeof v !== 'function'),
  isRegExp: v => v instanceof RegExp,
  isString: v => typeof v === 'string',
  isSymbol: v => typeof v === 'symbol',
  isUndefined: v => v === undefined,
};
for (const [name, fn] of Object.entries(shims)) {
  if (typeof util[name] !== 'function') util[name] = fn;
}
