import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const namespace = 'numismatics_treasury';
const resourceRoot = path.join(root, 'src', 'main', 'resources');

function mkdir(file) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
}

function writeJson(file, value) {
  mkdir(file);
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
}

const directionVectors = {
  north: [0, 0, -1],
  east: [1, 0, 0],
  south: [0, 0, 1],
  west: [-1, 0, 0],
  up: [0, 1, 0],
  down: [0, -1, 0]
};

function round(value) {
  const rounded = Number(value.toFixed(5));
  return Math.abs(rounded) < 0.00001 ? 0 : rounded;
}

function rotateVector(vector, rotation) {
  let [x, y, z] = vector;
  const [rx, ry, rz] = rotation.map(value => value * Math.PI / 180);

  [y, z] = [y * Math.cos(rx) - z * Math.sin(rx), y * Math.sin(rx) + z * Math.cos(rx)];
  [x, z] = [x * Math.cos(ry) + z * Math.sin(ry), -x * Math.sin(ry) + z * Math.cos(ry)];
  [x, y] = [x * Math.cos(rz) - y * Math.sin(rz), x * Math.sin(rz) + y * Math.cos(rz)];
  return [x, y, z];
}

function directionFor(vector) {
  const entry = Object.entries(directionVectors).find(([, candidate]) =>
    candidate.every((value, index) => Math.abs(value - Math.round(vector[index])) < 0.0001)
  );
  if (!entry) throw new Error(`Rotation does not resolve to a block face: ${vector}`);
  return entry[0];
}

function geometryFor(element) {
  const rotation = Array.isArray(element.rotation) ? element.rotation : [0, 0, 0];
  const activeAxes = rotation
    .map((value, index) => ({ value, index }))
    .filter(entry => Math.abs(entry.value) > 0.00001);
  const supported = activeAxes.length === 1
    && [22.5, 45].includes(Math.abs(activeAxes[0].value));

  if (activeAxes.length === 0 || supported) {
    return {
      from: element.from.map(round),
      to: element.to.map(round),
      rotation: supported ? {
        origin: element.origin.map(round),
        axis: ['x', 'y', 'z'][activeAxes[0].index],
        angle: activeAxes[0].value,
        rescale: Boolean(element.rescale)
      } : undefined,
      faceDirections: Object.fromEntries(Object.keys(directionVectors).map(key => [key, key]))
    };
  }

  if (!activeAxes.every(entry => Math.abs(entry.value / 90 - Math.round(entry.value / 90)) < 0.0001)) {
    throw new Error(`Unsupported compound rotation on element ${element.uuid}: ${rotation}`);
  }

  const transformed = [];
  for (const x of [element.from[0], element.to[0]]) {
    for (const y of [element.from[1], element.to[1]]) {
      for (const z of [element.from[2], element.to[2]]) {
        const relative = [x - element.origin[0], y - element.origin[1], z - element.origin[2]];
        const rotated = rotateVector(relative, rotation);
        transformed.push(rotated.map((value, index) => value + element.origin[index]));
      }
    }
  }

  return {
    from: [0, 1, 2].map(axis => round(Math.min(...transformed.map(point => point[axis])))),
    to: [0, 1, 2].map(axis => round(Math.max(...transformed.map(point => point[axis])))),
    rotation: undefined,
    faceDirections: Object.fromEntries(
      Object.entries(directionVectors).map(([direction, vector]) => [
        direction,
        directionFor(rotateVector(vector, rotation))
      ])
    )
  };
}

function convert(sourceName, outputName) {
  const sourceFile = path.join(root, 'p assets', sourceName + '.bbmodel');
  const model = JSON.parse(fs.readFileSync(sourceFile, 'utf8'));
  const resolution = model.resolution ?? { width: 16, height: 16 };
  const texture = model.textures?.[0];
  if (!texture?.source?.startsWith('data:image/png;base64,')) {
    throw new Error(`Embedded PNG missing in ${sourceFile}`);
  }
  const png = Buffer.from(texture.source.split(',', 2)[1], 'base64');
  const textureFile = path.join(
    resourceRoot, 'assets', namespace, 'textures', 'block', outputName + '.png'
  );
  mkdir(textureFile);
  fs.writeFileSync(textureFile, png);

  const elements = model.elements
    .filter(element => element.export !== false && element.visibility !== false)
    .map(element => {
      const geometry = geometryFor(element);
      const converted = {
        from: geometry.from,
        to: geometry.to,
        shade: element.shade !== false,
        faces: {}
      };
      if (geometry.rotation) converted.rotation = geometry.rotation;
      for (const [direction, face] of Object.entries(element.faces ?? {})) {
        if (face.texture === null || face.texture === undefined) continue;
        const target = {
          uv: [
            face.uv[0] * 16 / resolution.width,
            face.uv[1] * 16 / resolution.height,
            face.uv[2] * 16 / resolution.width,
            face.uv[3] * 16 / resolution.height
          ].map(value => Number(value.toFixed(5))),
          texture: '#0'
        };
        if (face.rotation) target.rotation = face.rotation;
        converted.faces[geometry.faceDirections[direction]] = target;
      }
      return converted;
    });

  writeJson(path.join(resourceRoot, 'assets', namespace, 'models', 'block', outputName + '.json'), {
    parent: 'minecraft:block/block',
    render_type: 'minecraft:cutout',
    ambientocclusion: model.ambientocclusion !== false,
    textures: { 0: `${namespace}:block/${outputName}`, particle: `${namespace}:block/${outputName}` },
    elements,
    display: model.display ?? {}
  });
}

convert('auction_block', 'auction_house');
